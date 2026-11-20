package hypothesisengine.control

import hypothesisengine.application.*
import hypothesisengine.domain.*
import hypothesisengine.infrastructure.*
import io.circe.Decoder
import io.circe.Encoder
import io.circe.parser.decode
import io.circe.syntax.*
import java.nio.file.{Files, Paths, StandardOpenOption}

object ReportJson:
  given Encoder[StageDecision] = Encoder.forProduct4("stage", "posterior", "gate", "decision")(value =>
    (value.stage, value.posterior, value.gate, value.decision))
  given Encoder[BreakpointView] = Encoder.forProduct3("coordinate", "execution_id", "detail")(value =>
    (value.coordinate, value.executionId, value.detail))
  given Encoder[LedgerStageReport] = Encoder.forProduct12(
    "stage", "hypothesis_id", "statement", "dtr_coordinates", "justification",
    "probability_for_correctness", "gate", "gap", "decision", "breakpoints",
    "fix", "history"
  )(value => (
    value.stage, value.hypothesisId, value.statement, value.dtrCoordinates, value.justification,
    value.probabilityForCorrectness, value.gate, value.gap, value.decision, value.breakpoints,
    value.fix, value.history
  ))

final class HypothesisEngine(runner: ProcessRunner, ledger: Ledger, clock: Clock):
  def execute(hypothesis: Hypothesis, definitions: Vector[ExperimentDefinition]): ExaminationOutcome =
    val priorHistory: Vector[StageDecision] = ledger.decisionHistory(hypothesis.id)
    val decision: Decision =
      LedgerValidator.requireAdmissible(hypothesis) match
        case Left(reason) => Decision.Blocked(reason)
        case Right(_) =>
          ledger.resetStageData(hypothesis.id)
          ledger.saveHypothesis(hypothesis)
          ledger.saveCoordinates(hypothesis.id, hypothesis.coordinates)
          ledger.saveExperiments(hypothesis.id, definitions)
          definitions.foreach { definition =>
            val raw: RawResult = runner.run(definition)
            ledger.saveRaw(raw)
            ResultNormalizer.validate(raw) match
              case Right(normalized) => ledger.saveNormalized(normalized)
              case Left(error) => System.err.println("REJECTED " + error)
          }
          LedgerValidator.requireComplete(ledger.missingFor(hypothesis, definitions)) match
            case Left(reason) =>
              val blocked: Decision = ExaminationGate.blocked(hypothesis, reason)
              ledger.saveDecision(hypothesis.id, blocked)
              blocked
            case Right(_) =>
              val results: Vector[(RawResult, NormalizedResult)] = ledger.resultsFor(hypothesis.id)
              val posterior: Double = BayesianEvaluator.update(hypothesis.prior, results.map((_._2)))
              ledger.saveCalculation(BayesianEvaluator.record(hypothesis.id, posterior, clock.now()))
              val decided: Decision = ExaminationGate.decide(hypothesis, posterior, results)
              ledger.saveDecision(hypothesis.id, decided)
              decided
    ExaminationOutcome(decision, StageReporter.build(hypothesis, decision, priorHistory))

object Main:
  given Decoder[HypothesisId] = Decoder.decodeString.map(HypothesisId.apply)
  given Decoder[DtrCoordinate] = Decoder.decodeString.map(DtrCoordinate.apply)
  given Decoder[Hypothesis] = Decoder.forProduct6(
    "id", "statement", "dtr_coordinates", "gate_probability_for_correctness", "prior", "justification"
  )(Hypothesis.apply)

  def main(args: Array[String]): Unit =
    if args.length < 2 then
      System.err.println("usage: Main <hypothesis.json> <experiments.jsonl>")
      System.exit(2)
    val hypothesisJson: String = new String(Files.readAllBytes(Paths.get(args(0))), "UTF-8")
    val hypothesis: Hypothesis = decode[Hypothesis](hypothesisJson).fold(
      error => throw new IllegalArgumentException(error.getMessage), identity)
    val definitions: Vector[ExperimentDefinition] = Files.readAllLines(Paths.get(args(1)).toAbsolutePath).toArray(new Array[String](0)).toVector
      .filter(_.trim.nonEmpty)
      .map { line =>
        ExperimentDefinitionCodec.parse(line).fold(
          error => throw new IllegalArgumentException(error), identity)
      }
    val clock: Clock = new SystemClock
    val ledgerPath: String = sys.props.getOrElse("hypothesis-engine.ledger.path", "ledger.db")
    val engine: HypothesisEngine = new HypothesisEngine(new ProcessAdapter(clock), new SqliteLedger(ledgerPath), clock)
    val outcome: ExaminationOutcome = engine.execute(hypothesis, definitions)
    import ReportJson.given
    val block: String = outcome.report.asJson.noSpaces
    println(block)
    val hypothesisPath: String = args(0)
    val reportPath: java.nio.file.Path = Paths.get(
      if hypothesisPath.endsWith(".hypotheses.json")
      then hypothesisPath.stripSuffix(".hypotheses.json") + ".ledger-report.jsonl"
      else hypothesisPath + ".ledger-report.jsonl"
    )
    Files.writeString(reportPath, block + System.lineSeparator, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    outcome.decision match
      case Decision.Green(_, posterior, gate) =>
        println("GREEN_LIGHT posterior=" + posterior + " gate=" + gate)
      case Decision.Red(_, posterior, gate, breakpoints, fix) =>
        println("RED_LIGHT posterior=" + posterior + " gate=" +
          " breakpoints=" + breakpoints.map(breakpoint => DtrCoordinate.value(breakpoint.coordinate)).mkString("|") +
          " fix=" + fix.description)
      case Decision.Blocked(reason) =>
        println("ADVISOR_ACTION_REQUIRED missing=" + reason.missing.mkString("|") +
          " corrections=" + reason.corrections.mkString("|"))