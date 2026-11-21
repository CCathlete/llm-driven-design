package hypothesisengine.application

import hypothesisengine.domain.*
import java.time.Instant

object LedgerValidator {
  val gateFloor: Double = 0.85d

  def requireComplete(missing: Vector[String]): Either[AdvisorActionRequired, Unit] =
    if missing.isEmpty then Right(())
    else Left(AdvisorActionRequired(missing, missing.map(name => "supply " + name + " to the ledger before examination")))

  def requireAdmissible(hypothesis: Hypothesis): Either[AdvisorActionRequired, Unit] =
    val floorMissing: Vector[String] =
      if hypothesis.gate >= gateFloor then Vector.empty[String]
      else Vector("gate_probability_for_correctness >= " + gateFloor.toString)
    val floorCorrections: Vector[String] =
      if hypothesis.gate >= gateFloor then Vector.empty[String]
      else Vector("raise gate_probability_for_correctness to at least " + gateFloor.toString + " (found " + hypothesis.gate.toString + ")")
    val justificationMissing: Vector[String] =
      if hypothesis.justification.trim.nonEmpty then Vector.empty[String]
      else Vector("justification")
    val justificationCorrections: Vector[String] =
      if hypothesis.justification.trim.nonEmpty then Vector.empty[String]
      else Vector("supply a non-empty Advisor-authored justification for the hypothesis")
    val missing: Vector[String] = floorMissing ++ justificationMissing
    val corrections: Vector[String] = floorCorrections ++ justificationCorrections
    if missing.isEmpty then Right(()) else Left(AdvisorActionRequired(missing, corrections))
}

object CoverageFalsificationEvaluator {
  val ceiling: Double = 0.95d
  private val decay: Double = 0.5d

  def record(hypothesisId: HypothesisId, posterior: Double, at: Instant): ProbabilityCalculation =
    ProbabilityCalculation(hypothesisId, posterior, at)

  def update(prior: Double, coordinates: Vector[DtrCoordinate], evidence: Vector[(DtrCoordinate, Boolean)]): Double =
    if evidence.isEmpty then prior
    else
      val grouped: Map[DtrCoordinate, Vector[Boolean]] = evidence.groupMap(_._1)(_._2)
      val failed: Set[DtrCoordinate] = grouped.collect {
        case (coordinate, outcomes) if outcomes.contains(false) => coordinate
      }.toSet
      def evidenceFor(coordinate: DtrCoordinate): Double =
        if failed.contains(coordinate) then 0.0d
        else
          val passes: Int = grouped.getOrElse(coordinate, Vector.empty).count(identity)
          if passes == 0 then 0.0d else 1.0d - math.pow(decay, math.min(passes, 52).toDouble)
      val denominator: Double = coordinates.length.toDouble.max(1.0d)
      val coverage: Double = coordinates.map(evidenceFor).sum / denominator
      val score: Double = prior + (ceiling - prior) * coverage
      if failed.nonEmpty then math.min(score, prior) else score
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