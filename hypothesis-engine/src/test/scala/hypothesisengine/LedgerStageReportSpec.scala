package hypothesisengine

import hypothesisengine.control.{HypothesisEngine, ReportJson}
import hypothesisengine.domain.*
import hypothesisengine.infrastructure.SqliteLedger
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import java.time.Instant
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

final class LedgerStageReportSpec extends AnyFunSpec with Matchers:
  private def freshLedger(): SqliteLedger =
    val dir: Path = Files.createTempDirectory("hypothesis-report")
    new SqliteLedger(dir.resolve("ledger.db").toString)

  private val hypothesis: Hypothesis = Hypothesis(
    HypothesisId("H-REPORT"), "report progression",
    Vector(DtrCoordinate("FILE.a.scala"), DtrCoordinate("TYPE.b.Domain")),
    0.85d, 0.5d, "report justification")

  private def definitions: Vector[ExperimentDefinition] =
    (1 to 5).map { index =>
      ExperimentDefinition("EXP-" + index.toString, hypothesis.id, Runtime.Python,
      Vector("python3", "-c", "print(1)"), ".", 30, "v1",
      Vector(DtrCoordinate("FILE.a.scala"), DtrCoordinate("TYPE.b.Domain")))
    }.toVector

  private def evidence(passed: Boolean, tag: String): Vector[RawResult] =
    definitions.zipWithIndex.map { case (definition, index) =>
      RawResult(
        ExecutionId("exec-" + tag + "-" + index.toString), hypothesis.id, definition.experimentId, "v1",
        definition.command, "rev", 30, if passed then 0 else 1,
        "{\"passed\":" + passed.toString + ",\"detail\":\"stage " + tag + " probe " + index.toString + "\"}", "", 1L,
        Instant.parse("2026-01-01T00:00:00Z"))
    }

  describe("per-stage ledger report"):
    it("emits three cumulative stages with correct fields and a stable encoding"):
      val ledger: SqliteLedger = freshLedger()
      val stages: Vector[LedgerStageReport] = Vector(
        new HypothesisEngine(new ScriptedRunner(evidence(false, "one")), ledger, new FixedClock).execute(hypothesis, definitions),
        new HypothesisEngine(new ScriptedRunner(evidence(false, "two")), ledger, new FixedClock).execute(hypothesis, definitions),
        new HypothesisEngine(new ScriptedRunner(evidence(true, "three")), ledger, new FixedClock).execute(hypothesis, definitions)
      ).map(_.report)
      stages.length shouldBe 3
      stages.map(_.stage) shouldBe Vector(1, 2, 3)
      stages.map(_.decision) shouldBe Vector("RED", "RED", "GREEN")
      stages.foreach { report =>
        report.hypothesisId shouldBe "H-REPORT"
        report.statement shouldBe "report progression"
        report.dtrCoordinates shouldBe Vector("FILE.a.scala", "TYPE.b.Domain")
        report.justification shouldBe "report justification"
        report.gate shouldBe 0.85d
        (report.gap - (report.gate - report.probabilityForCorrectness)) shouldBe 0.0d
      }
      stages.head.breakpoints.nonEmpty shouldBe true
      stages.head.fix.isDefined shouldBe true
      stages.last.breakpoints.isEmpty shouldBe true
      stages.last.history.length shouldBe 3
      stages.last.history.map(_.stage) shouldBe Vector(1, 2, 3)
      import ReportJson.given
      val block: String = stages.last.asJson.noSpaces
      block shouldBe stages.last.asJson.noSpaces
      block.contains("probability_for_correctness") shouldBe true
      block.contains("justification") shouldBe true
      block.contains("history") shouldBe true