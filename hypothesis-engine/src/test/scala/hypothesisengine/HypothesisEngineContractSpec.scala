package hypothesisengine

import hypothesisengine.application.{ResultNormalizer, ExaminationGate}
import hypothesisengine.domain.*
import hypothesisengine.infrastructure.ExperimentDefinitionCodec
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class HypothesisEngineContractSpec extends AnyFunSpec with Matchers:
  describe("experiment contract"):
    it("accepts python and scala definitions and rejects an unknown runtime"):
      val pythonJson: String = "{\"experiment_id\":\"EXP-001\",\"hypothesis_id\":\"H-001\",\"runtime\":\"python\",\"command\":[\"python\"],\"working_directory\":\".\",\"timeout_seconds\":60,\"test_artifact_version\":\"v1\"}"
      val scalaJson: String = pythonJson.replace("python", "scala")
      val badJson: String = pythonJson.replace("python", "ruby")
      ExperimentDefinitionCodec.parse(pythonJson).map(_.runtime) shouldBe Right(Runtime.Python)
      ExperimentDefinitionCodec.parse(scalaJson).map(_.runtime) shouldBe Right(Runtime.Scala)
      ExperimentDefinitionCodec.parse(badJson).isLeft shouldBe true

    it("normalizes a structured result, rejects malformed json and falls back to exit code"):
      val good: RawResult = Fixtures.raw(true, "ok")
      val malformed: RawResult = good.copy(stdout = "{not json")
      val plain: RawResult = good.copy(stdout = "", exitCode = 0)
      ResultNormalizer.validate(good).map(_.passed) shouldBe Right(true)
      ResultNormalizer.validate(malformed).isLeft shouldBe true
      ResultNormalizer.validate(plain).map(_.passed) shouldBe Right(true)

    it("ignores an agent supplied decision input"):
      val first: Decision = ExaminationGate.decide(Fixtures.hypothesis, 0.9d, Vector.empty)
      val second: Decision = ExaminationGate.decide(Fixtures.hypothesis, 0.9d, Vector.empty)
      first shouldBe second
      first.isInstanceOf[Decision.Green] shouldBe true

    it("keeps domain and application free of runtime and persistence imports"):
      val domain: String = scala.io.Source.fromFile("src/main/scala/hypothesisengine/domain/Model.scala").mkString
      val application: String = scala.io.Source.fromFile("src/main/scala/hypothesisengine/application/Ports.scala").mkString
      domain.contains("hypothesisengine.infrastructure") shouldBe false
      application.contains("hypothesisengine.infrastructure") shouldBe false