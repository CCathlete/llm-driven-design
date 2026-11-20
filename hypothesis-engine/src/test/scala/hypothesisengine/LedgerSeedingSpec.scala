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
    Vector(DtrCoordinate("TYPE.a.Domain")), 0.85d, 0.5d,
    "seed justification for ledger progression")

  private def seedDefinitions: Vector[ExperimentDefinition] =
    (1 to 5).map { index =>
      ExperimentDefinition("EXP-SEED-" + index.toString, seedHypothesis.id, Runtime.Python,
      Vector("python3", "-c", "print(1)"), ".", 30, "v1")
    }.toVector

  private def passingEvidence: Vector[RawResult] =
    seedDefinitions.zipWithIndex.map { case (definition, index) =>
      RawResult(ExecutionId("exec-seed-" + index.toString), seedHypothesis.id, definition.experimentId, "v1",
        definition.command, "rev", 30, 0,
        "{\"passed\":true,\"detail\":\"seeded evidence \" + index.toString}", "", 1L,
        Instant.parse("2026-01-01T00:00:00Z"))
    }

  test("execute seeds hypothesis, coordinates and experiment definition into a fresh ledger") {
    val ledger: SqliteLedger = freshLedger()
    val hypothesis: Hypothesis = seedHypothesis
    val definitions: Vector[ExperimentDefinition] = seedDefinitions
    assert(ledger.missingFor(hypothesis, definitions).nonEmpty, "fresh ledger must be incomplete before execute")
    val engine: HypothesisEngine = new HypothesisEngine(new ScriptedRunner(passingEvidence), ledger, new FixedClock)
    engine.execute(hypothesis, definitions)
    assert(ledger.missingFor(hypothesis, definitions).isEmpty, "execute must seed all required ledger records")
  }

  test("a fresh ledger reaches GREEN_LIGHT with five passing probes at the floor gate") {
    val ledger: SqliteLedger = freshLedger()
    val engine: HypothesisEngine = new HypothesisEngine(new ScriptedRunner(passingEvidence), ledger, new FixedClock)
    engine.execute(seedHypothesis, seedDefinitions).decision match
      case Decision.Green(_, posterior, gate) => assert(posterior >= gate)
      case other => fail("expected GREEN_LIGHT, got " + other)
  }