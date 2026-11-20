package hypothesisengine.control

import hypothesisengine.application.*
import hypothesisengine.domain.*
import hypothesisengine.infrastructure.*
import io.circe.Decoder
import io.circe.parser.decode
import java.nio.file.{Files, Paths}

final class HypothesisEngine(runner: ProcessRunner, ledger: Ledger, clock: Clock):
  def execute(hypothesis: Hypothesis, definitions: Vector[ExperimentDefinition]): Decision =
    definitions.foreach { definition =>
      val raw: RawResult = runner.run(definition)
      ledger.saveRaw(raw)
      ResultNormalizer.validate(raw) match
        case Right(normalized) => ledger.saveNormalized(normalized)
        case Left(error) => System.err.println("REJECTED " + error)
    }
    LedgerValidator.requireComplete(ledger.missingFor(hypothesis, definitions)) match
      case Left(reason) =>
        val decision: Decision = ExaminationGate.blocked(hypothesis, reason)
        ledger.saveDecision(hypothesis.id, decision)
        decision
      case Right(_) =>
        val results: Vector[(RawResult, NormalizedResult)] = ledger.resultsFor(hypothesis.id)
        val posterior: Double = BayesianEvaluator.update(hypothesis.prior, results.map((_._2)))
        ledger.saveCalculation(BayesianEvaluator.record(hypothesis.id, posterior, clock.now()))
        val decision: Decision = ExaminationGate.decide(hypothesis, posterior, results)
        ledger.saveDecision(hypothesis.id, decision)
        decision

object Main:
  given Decoder[HypothesisId] = Decoder.decodeString.map(HypothesisId.apply)
  given Decoder[DtrCoordinate] = Decoder.decodeString.map(DtrCoordinate.apply)
  given Decoder[Hypothesis] = Decoder.forProduct5(
    "id", "statement", "dtr_coordinates", "gate_probability_for_correctness", "prior"
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
    val engine: HypothesisEngine = new HypothesisEngine(new ProcessAdapter(clock), new SqliteLedger, clock)
    engine.execute(hypothesis, definitions) match
      case Decision.Green(_, posterior, gate) =>
        println("GREEN_LIGHT posterior=" + posterior + " gate=" + gate)
      case Decision.Red(_, posterior, gate, breakpoints, fix) =>
        println("RED_LIGHT posterior=" + posterior + " gate=" + gate +
          " breakpoints=" + breakpoints.map(breakpoint => DtrCoordinate.value(breakpoint.coordinate)).mkString("|") +
          " fix=" + fix.description)
      case Decision.Blocked(reason) =>
        println("ADVISOR_ACTION_REQUIRED missing=" + reason.missing.mkString("|") +
          " corrections=" + reason.corrections.mkString("|"))