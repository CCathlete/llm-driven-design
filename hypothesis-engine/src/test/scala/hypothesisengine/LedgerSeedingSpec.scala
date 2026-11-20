package hypothesisengine

import hypothesisengine.application.*
import hypothesisengine.control.HypothesisEngine
import hypothesisengine.domain.*
import hypothesisengine.infrastructure.SqliteLedger
import java.nio.file.{Files, Path}
import java.time.Instant
import org.scalatest.funsuite.AnyFunSuite

final class LedgerSeedingSpec extends AnyFunSuite:
  private def freshLedger(): SqliteLedger =
    val dir: Path = Files.createTempDirectory("hypothesis-engine-seed")
    new SqliteLedger(dir.resolve("ledger.db").toString)

  private def seedHypothesis: Hypothesis = Hypothesis(
    HypothesisId("H-SEED"), "state stays consistent",
    Vector(DtrCoordinate("TYPE.a.Domain")), 0.6d, 0.5d)

  private def seedDefinition: ExperimentDefinition = ExperimentDefinition(
    "EXP-SEED", seedHypothesis.id, Runtime.Python, Vector("python3", "-c", "print(1)"),
    ".", 30, "v1")

  private def passingRaw: RawResult = RawResult(
    ExecutionId("exec-seed"), seedHypothesis.id, seedDefinition.experimentId, "v1",
    seedDefinition.command, "rev", 30, 0,
    "{\"passed\":true,\"detail\":\"seeded evidence\"}", "", 1L,
    Instant.parse("2026-01-01T00:00:00Z"))

  test("execute seeds hypothesis, coordinates and experiment definition into a fresh ledger") {
    val ledger: SqliteLedger = freshLedger()
    val hypothesis: Hypothesis = seedHypothesis
    val definitions: Vector[ExperimentDefinition] = Vector(seedDefinition)
    assert(ledger.missingFor(hypothesis, definitions).nonEmpty, "fresh ledger must be incomplete before execute")
    val engine: HypothesisEngine = new HypothesisEngine(new ScriptedRunner(Vector(passingRaw)), ledger, new FixedClock)
    engine.execute(hypothesis, definitions)
    assert(ledger.missingFor(hypothesis, definitions).isEmpty, "execute must seed all required ledger records")
  }

  test("a fresh ledger reaches GREEN_LIGHT with passing evidence") {
    val ledger: SqliteLedger = freshLedger()
    val engine: HypothesisEngine = new HypothesisEngine(new ScriptedRunner(Vector(passingRaw)), ledger, new FixedClock)
    engine.execute(seedHypothesis, Vector(seedDefinition)) match
      case Decision.Green(_, posterior, gate) => assert(posterior >= gate)
      case other => fail("expected GREEN_LIGHT, got " + other)
  }
