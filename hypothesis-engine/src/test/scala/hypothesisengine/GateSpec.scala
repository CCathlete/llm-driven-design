package hypothesisengine

import hypothesisengine.application.{BayesianEvaluator, ExaminationGate, ResultNormalizer}
import hypothesisengine.domain.*
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class GateSpec extends AnyFunSpec with Matchers:
  describe("bayesian evaluation and examination gate"):
    it("is deterministic for identical evidence"):
      val results: Vector[NormalizedResult] = Vector(Fixtures.raw(true, "a"), Fixtures.raw(false, "b")).map(raw => ResultNormalizer.validate(raw).toOption.get)
      val first: Double = BayesianEvaluator.update(0.5d, results)
      val second: Double = BayesianEvaluator.update(0.5d, results)
      first shouldBe second

    it("grants green only at or above the configured gate"):
      ExaminationGate.decide(Fixtures.hypothesis, 0.80d, Vector.empty).isInstanceOf[Decision.Green] shouldBe true
      ExaminationGate.decide(Fixtures.hypothesis, 0.79d, Vector.empty).isInstanceOf[Decision.Red] shouldBe true

    it("reports dtr coordinates of breakpoints with a derived fix on red"):
      val failing: Vector[(RawResult, NormalizedResult)] = Vector((Fixtures.raw(false, "assertion failed"), ResultNormalizer.validate(Fixtures.raw(false, "assertion failed")).toOption.get))
      val decision: Decision = ExaminationGate.decide(Fixtures.hypothesis, 0.10d, failing)
      decision match
        case Decision.Red(_, _, _, breakpoints, fix) =>
          breakpoints.nonEmpty shouldBe true
          DtrCoordinate.value(breakpoints.head.coordinate) shouldBe "TYPE.a.Domain"
          fix.targets.nonEmpty shouldBe true
          fix.description.nonEmpty shouldBe true
        case _ => fail("expected a red decision")

    it("blocks before evaluation when the ledger is incomplete"):
      ExaminationGate.blocked(Fixtures.hypothesis, AdvisorActionRequired(Vector("raw evidence"), Vector("supply raw evidence"))).isInstanceOf[Decision.Blocked] shouldBe true