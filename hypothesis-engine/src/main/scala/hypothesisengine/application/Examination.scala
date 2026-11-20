package hypothesisengine.application

import hypothesisengine.domain.*
import java.time.Instant

object LedgerValidator {
  def requireComplete(missing: Vector[String]): Either[AdvisorActionRequired, Unit] =
    if missing.isEmpty then Right(())
    else Left(AdvisorActionRequired(missing, missing.map(name => "supply " + name + " to the ledger before examination")))
}

object BayesianEvaluator {
  private val concentration: Double = 2.0d

  def update(prior: Double, results: Vector[NormalizedResult]): Double =
    val passes: Double = results.count(_.passed).toDouble
    val total: Double = results.length.toDouble
    (prior * concentration + passes) / (concentration + total)

  def record(hypothesisId: HypothesisId, posterior: Double, at: Instant): ProbabilityCalculation =
    ProbabilityCalculation(hypothesisId, posterior, at)
}

object RemediationReporter {
  def breakpointsFor(hypothesis: Hypothesis, results: Vector[(RawResult, NormalizedResult)]): Vector[Breakpoint] =
    results.filterNot((_._2.passed)).zipWithIndex.map { case ((raw, normalized), index) =>
      val coordinate: DtrCoordinate = hypothesis.coordinates.lift(index)
        .getOrElse(hypothesis.coordinates.headOption.getOrElse(DtrCoordinate("UNKNOWN")))
      Breakpoint(coordinate, raw.executionId, normalized.detail)
    }

  def suggest(hypothesis: Hypothesis, breakpoints: Vector[Breakpoint]): SuggestedFix =
    val targets: Vector[DtrCoordinate] = breakpoints.map(_.coordinate).distinct
    SuggestedFix(targets, "Revise the implementation at " + targets.map(DtrCoordinate.value).mkString(", ") + " using the failing evidence recorded on the breakpoints, then re-run the experiments for " + HypothesisId.value(hypothesis.id))
}

object ExaminationGate {
  def blocked(hypothesis: Hypothesis, reason: AdvisorActionRequired): Decision =
    Decision.Blocked(reason)

  def decide(hypothesis: Hypothesis, posterior: Double, results: Vector[(RawResult, NormalizedResult)]): Decision =
    if posterior >= hypothesis.gate then Decision.Green(hypothesis.id, posterior, hypothesis.gate)
    else
      val breakpoints: Vector[Breakpoint] = RemediationReporter.breakpointsFor(hypothesis, results)
      Decision.Red(hypothesis.id, posterior, hypothesis.gate, breakpoints, RemediationReporter.suggest(hypothesis, breakpoints))
}