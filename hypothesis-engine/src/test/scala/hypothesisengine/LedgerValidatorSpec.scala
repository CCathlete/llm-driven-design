package hypothesisengine

import hypothesisengine.application.LedgerValidator
import hypothesisengine.control.HypothesisEngine
import hypothesisengine.domain.*
import hypothesisengine.infrastructure.SqliteLedger
import java.nio.file.{Files, Path}
import java.sql.DriverManager
import java.time.Instant
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

final class LedgerValidatorSpec extends AnyFunSpec with Matchers:
  private def freshDbPath(): String =
    Files.createTempDirectory("hypothesis-validator").resolve("ledger.db").toString

  private def definitionsFor(id: HypothesisId): Vector[ExperimentDefinition] =
    (1 to 5).map { index =>
      ExperimentDefinition("EXP-" + index.toString, id, Runtime.Python,
      Vector("python3", "-c", "print(1)"), ".", 30, "v1")
    }.toVector

  private def rawsFor(id: HypothesisId, definitions: Vector[ExperimentDefinition]): Vector[RawResult] =
    definitions.zipWithIndex.map { case (definition, index) =>
      RawResult(ExecutionId("exec-" + definition.experimentId), id, definition.experimentId, "v1",
        definition.command, "rev", 30, 0,
        "{\"passed\":true,\"detail\":\"probe " + index.toString + "\"}", "", 1L,
        Instant.parse("2026-01-01T00:00:00Z"))
    }

  describe("hypothesis admissibility"):
    it("rejects a gate below the inclusive 0.85 floor"):
      val below: Hypothesis = Fixtures.hypothesis.copy(gate = 0.849d, justification = "floor probe")
      val reason: AdvisorActionRequired = LedgerValidator.requireAdmissible(below).left.getOrElse(AdvisorActionRequired(Vector.empty, Vector.empty))
      reason.missing.exists(_.contains("0.85")) shouldBe true

    it("accepts a gate exactly at the floor"):
      val atFloor: Hypothesis = Fixtures.hypothesis.copy(gate = 0.85d, justification = "floor probe")
      LedgerValidator.requireAdmissible(atFloor).isRight shouldBe true

    it("rejects missing, empty and whitespace justification"):
      val base: Hypothesis = Fixtures.hypothesis.copy(gate = 0.85d)
      Vector("", "   ", "\n\t").foreach { blank =>
        LedgerValidator.requireAdmissible(base.copy(justification = blank)).isLeft shouldBe true
      }

    it("writes no calculation and no decision for a below-floor hypothesis"):
      val db: String = freshDbPath()
      val ledger: SqliteLedger = new SqliteLedger(db)
      val hypothesis: Hypothesis = Fixtures.hypothesis.copy(id = HypothesisId("H-FLOOR"), gate = 0.849d, justification = "floor probe")
      val definitions: Vector[ExperimentDefinition] = definitionsFor(hypothesis.id)
      val runner: ScriptedRunner = new ScriptedRunner(rawsFor(hypothesis.id, definitions))
      new HypothesisEngine(runner, ledger, new FixedClock).execute(hypothesis, definitions)
      val conn = DriverManager.getConnection("jdbc:sqlite:" + db)
      val calculations: Int = conn.createStatement().executeQuery("SELECT COUNT(*) FROM calculations").getInt(1)
      val decisions: Int = conn.createStatement().executeQuery("SELECT COUNT(*) FROM decisions").getInt(1)
      conn.close()
      calculations shouldBe 0
      decisions shouldBe 0

    it("round-trips justification through the hypotheses table and keeps seven tables"):
      val db: String = freshDbPath()
      val ledger: SqliteLedger = new SqliteLedger(db)
      val hypothesis: Hypothesis = Fixtures.hypothesis.copy(id = HypothesisId("H-JUST"), gate = 0.85d, justification = "round trip proof")
      ledger.saveHypothesis(hypothesis)
      val conn = DriverManager.getConnection("jdbc:sqlite:" + db)
      val stored: String = conn.createStatement().executeQuery("SELECT justification FROM hypotheses WHERE id = 'H-JUST'").getString(1)
      val tables: Int = conn.createStatement().executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table'").getInt(1)
      conn.close()
      stored shouldBe "round trip proof"
      tables shouldBe 7