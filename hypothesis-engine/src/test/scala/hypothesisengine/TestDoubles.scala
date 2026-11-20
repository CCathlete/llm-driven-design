package hypothesisengine

import hypothesisengine.application.{Clock, Ledger, ProcessRunner}
import hypothesisengine.domain.*
import java.time.Instant

final class FixedClock extends Clock:
  override def now(): Instant = Instant.parse("2026-01-01T00:00:00Z")

final class ScriptedRunner(results: Vector[RawResult]) extends ProcessRunner:
  private var index: Int = 0
  override def run(definition: ExperimentDefinition): RawResult =
    val result: RawResult = results(index % results.length)
    index = index + 1
    result

final class MemoryLedger(var missing: Vector[String] = Vector.empty) extends Ledger:
  val rawRows: scala.collection.mutable.ArrayBuffer[RawResult] = scala.collection.mutable.ArrayBuffer.empty
  val normalizedRows: scala.collection.mutable.ArrayBuffer[NormalizedResult] = scala.collection.mutable.ArrayBuffer.empty
  val calculationRows: scala.collection.mutable.ArrayBuffer[ProbabilityCalculation] = scala.collection.mutable.ArrayBuffer.empty
  val decisionRows: scala.collection.mutable.ArrayBuffer[(HypothesisId, Decision)] = scala.collection.mutable.ArrayBuffer.empty
  override def saveRaw(result: RawResult): Either[String, Unit] = { rawRows += result; Right(()) }
  override def saveNormalized(result: NormalizedResult): Either[String, Unit] = { normalizedRows += result; Right(()) }
  override def saveCalculation(calculation: ProbabilityCalculation): Either[String, Unit] = { calculationRows += calculation; Right(()) }
  override def saveDecision(id: HypothesisId, decision: Decision): Either[String, Unit] = { decisionRows += ((id, decision)); Right(()) }
  override def missingFor(hypothesis: Hypothesis, experiments: Vector[ExperimentDefinition]): Vector[String] = missing
  override def resultsFor(id: HypothesisId): Vector[(RawResult, NormalizedResult)] =
    rawRows.zip(normalizedRows).filter((raw, _) => raw.hypothesisId == id).toVector

object Fixtures:
  val hypothesis: Hypothesis = Hypothesis(HypothesisId("H-001"), "state stays consistent", Vector(DtrCoordinate("TYPE.a.Domain"), DtrCoordinate("FILE.src/a.scala")), 0.80d, 0.50d)
  val pythonDefinition: ExperimentDefinition = ExperimentDefinition("EXP-001", hypothesis.id, Runtime.Python, Vector("python", "-m", "pytest"), "./experiments/python/state", 60, "v1")
  val scalaDefinition: ExperimentDefinition = ExperimentDefinition("EXP-002", hypothesis.id, Runtime.Scala, Vector("sbt", "testOnly", "StateConsistencySpec"), "./experiments/state-scala", 120, "v1")
  def raw(passed: Boolean, detail: String): RawResult = RawResult(
    ExecutionId(java.util.UUID.randomUUID().toString), hypothesis.id, "EXP-001", "v1",
    Vector("python", "-m", "pytest"), Map("SOURCE_REVISION" -> "abc123"), "abc123", 60,
    if passed then 0 else 1, "{\"passed\":" + passed + ",\"detail\":\"" + detail + "\"}", "", 12L,
    Instant.parse("2026-01-01T00:00:00Z"))