package hypothesisengine

import hypothesisengine.application.{BayesianEvaluator, LedgerValidator, ResultNormalizer}
import hypothesisengine.control.HypothesisEngine
import hypothesisengine.domain.*
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class LedgerSpec extends AnyFunSpec with Matchers:
  describe("evidence and fail-closed ledger"):
    it("persists raw evidence before a calculation and keeps both"):
      val ledger: MemoryLedger = new MemoryLedger
      val runner: ScriptedRunner = new ScriptedRunner(Vector(Fixtures.raw(true, "ok")))
      new HypothesisEngine(runner, ledger, new FixedClock).execute(Fixtures.hypothesis, Vector(Fixtures.pythonDefinition))
      ledger.rawRows.length shouldBe 1
      ledger.normalizedRows.length shouldBe 1
      ledger.calculationRows.length shouldBe 1
      ledger.rawRows.head.executionId shouldBe ledger.normalizedRows.head.executionId

    it("records immutable execution identity, version and reproducibility metadata"):
      val raw: RawResult = Fixtures.raw(false, "failed")
      ExecutionId.value(raw.executionId).nonEmpty shouldBe true
      raw.testArtifactVersion shouldBe "v1"
      raw.sourceRevision shouldBe "abc123"
      raw.command shouldBe Vector("python", "-m", "pytest")
      raw.timeoutSeconds shouldBe 60

    it("fails closed and names every missing datum"):
      val missing: Vector[String] = Vector("hypothesis record", "raw evidence")
      val verdict = LedgerValidator.requireComplete(missing)
      verdict.isLeft shouldBe true
      val reason: AdvisorActionRequired = verdict.left.getOrElse(AdvisorActionRequired(Vector.empty, Vector.empty))
      reason.missing shouldBe missing
      reason.corrections.length shouldBe missing.length

    it("holds seven distinct ledger record types in the sql schema"):
      val schema: String = scala.io.Source.fromFile("src/main/resources/ledger-schema.sql").mkString
      val tables: Seq[String] = Seq("hypotheses", "coordinates", "experiments", "executions", "evidence", "calculations", "decisions")
      tables.foreach(table => schema.contains("CREATE TABLE IF NOT EXISTS " + table) shouldBe true)