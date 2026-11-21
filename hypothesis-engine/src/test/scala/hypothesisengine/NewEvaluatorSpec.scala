package hypothesisengine

import hypothesisengine.application.CoverageFalsificationEvaluator
import hypothesisengine.application.ExaminationGate
import hypothesisengine.domain.*
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

final class NewEvaluatorSpec extends AnyFunSuite with Matchers:
  private val prior: Double = 0.5d
  private val coords: Vector[DtrCoordinate] = Vector(
    DtrCoordinate("TYPE.a.Domain"), DtrCoordinate("TYPE.b.Domain"),
    DtrCoordinate("TYPE.c.Domain"), DtrCoordinate("TYPE.d.Domain"))

  private def passes(coordinate: DtrCoordinate, count: Int): Vector[(DtrCoordinate, Boolean)] =
    Vector.fill(count)((coordinate, true))

  test("T3 per-coordinate evidence diminishes and stays below the ceiling") {
    val e1: Double = CoverageFalsificationEvaluator.update(prior, coords.take(1), passes(coords.head, 1))
    val e2: Double = CoverageFalsificationEvaluator.update(prior, coords.take(1), passes(coords.head, 2))
    val e3: Double = CoverageFalsificationEvaluator.update(prior, coords.take(1), passes(coords.head, 3))
    val e100: Double = CoverageFalsificationEvaluator.update(prior, coords.take(1), passes(coords.head, 100))
    (e1 - prior) / (0.95d - prior) shouldBe (0.5d +- 1e-9)
    (e2 - prior) / (0.95d - prior) shouldBe (0.75d +- 1e-9)
    (e3 - prior) / (0.95d - prior) shouldBe (0.875d +- 1e-9)
    e100 should be < 0.95d
    (e2 - e1) should be > (e3 - e2)
  }

  test("T4 coverage is the mean over all declared coordinates") {
    val threeOfFour: Double = CoverageFalsificationEvaluator.update(prior, coords, coords.take(3).flatMap(c => passes(c, 3)))
    val fourOfFour: Double = CoverageFalsificationEvaluator.update(prior, coords, coords.flatMap(c => passes(c, 3)))
    threeOfFour should be < 0.85d
    fourOfFour should be >= 0.85d
    val oneAThreeB: Double = CoverageFalsificationEvaluator.update(prior, coords.take(2), passes(coords(0), 3) ++ passes(coords(1), 1))
    val zeroB: Double = CoverageFalsificationEvaluator.update(prior, coords.take(2), passes(coords(0), 3))
    oneAThreeB should be > zeroB
  }

  test("T5 a single failure caps the score at the prior and forces RED") {
    val evidence: Vector[(DtrCoordinate, Boolean)] = coords.flatMap(c => passes(c, 5)) :+ ((coords.head, false))
    val score: Double = CoverageFalsificationEvaluator.update(prior, coords, evidence)
    score shouldBe (prior +- 1e-9)
    val hypothesis: Hypothesis = Fixtures.hypothesis.copy(coordinates = coords, gate = 0.85d, prior = prior)
    ExaminationGate.decide(hypothesis, score, Vector.empty) match
      case Decision.Red(_, _, _, _, _) => succeed
      case other => fail("expected RED, got " + other.toString)
  }

  test("T6 finite evidence stays below one and no evidence yields the prior") {
    val score: Double = CoverageFalsificationEvaluator.update(prior, coords.take(1), passes(coords.head, 100))
    score should be <= 0.95d
    score should be < 1.0d
    CoverageFalsificationEvaluator.update(prior, coords, Vector.empty) shouldBe (prior +- 1e-9)
  }

  test("T8 identical inputs give identical scores") {
    val evidence: Vector[(DtrCoordinate, Boolean)] = coords.take(2).flatMap(c => passes(c, 2))
    val first: Double = CoverageFalsificationEvaluator.update(prior, coords, evidence)
    val second: Double = CoverageFalsificationEvaluator.update(prior, coords, evidence)
    first shouldBe second
  }