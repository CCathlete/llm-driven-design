package hypothesisengine.domain

import java.time.Instant

opaque type HypothesisId = String
object HypothesisId:
  def apply(value: String): HypothesisId = value
  def value(id: HypothesisId): String = id

opaque type DtrCoordinate = String
object DtrCoordinate:
  def apply(value: String): DtrCoordinate = value
  def value(coordinate: DtrCoordinate): String = coordinate

opaque type ExecutionId = String
object ExecutionId:
  def apply(value: String): ExecutionId = value
  def value(id: ExecutionId): String = id

enum Runtime:
  case Python, Scala

final case class Hypothesis(
  id: HypothesisId,
  statement: String,
  coordinates: Vector[DtrCoordinate],
  gate: Double,
  prior: Double,
  justification: String = ""
)

final case class ExperimentDefinition(
  experimentId: String,
  hypothesisId: HypothesisId,
  runtime: Runtime,
  command: Vector[String],
  workingDirectory: String,
  timeoutSeconds: Int,
  testArtifactVersion: String
)

final case class RawResult(
  executionId: ExecutionId,
  hypothesisId: HypothesisId,
  experimentId: String,
  testArtifactVersion: String,
  command: Vector[String],
  sourceRevision: String,
  timeoutSeconds: Int,
  exitCode: Int,
  stdout: String,
  stderr: String,
  durationMillis: Long,
  recordedAt: Instant
)

final case class NormalizedResult(
  executionId: ExecutionId,
  hypothesisId: HypothesisId,
  passed: Boolean,
  detail: String
)

final case class Breakpoint(
  coordinate: DtrCoordinate,
  executionId: ExecutionId,
  detail: String
)

final case class SuggestedFix(
  targets: Vector[DtrCoordinate],
  description: String
)

final case class AdvisorActionRequired(
  missing: Vector[String],
  corrections: Vector[String]
)

final case class ProbabilityCalculation(
  hypothesisId: HypothesisId,
  posterior: Double,
  calculatedAt: Instant
)

enum Decision:
  case Green(hypothesisId: HypothesisId, posterior: Double, gate: Double)
  case Red(hypothesisId: HypothesisId, posterior: Double, gate: Double, breakpoints: Vector[Breakpoint], fix: SuggestedFix)
  case Blocked(reason: AdvisorActionRequired)

final case class StageDecision(
  stage: Int,
  posterior: Double,
  gate: Double,
  decision: String
)

final case class BreakpointView(
  coordinate: String,
  executionId: String,
  detail: String
)

final case class LedgerStageReport(
  stage: Int,
  hypothesisId: String,
  statement: String,
  dtrCoordinates: Vector[String],
  justification: String,
  probabilityForCorrectness: Double,
  gate: Double,
  gap: Double,
  decision: String,
  breakpoints: Vector[BreakpointView],
  fix: Option[String],
  history: Vector[StageDecision]
)

final case class ExaminationOutcome(
  decision: Decision,
  report: LedgerStageReport
)