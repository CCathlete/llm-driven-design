package hypothesisengine

import hypothesisengine.domain.*
import hypothesisengine.infrastructure.ExperimentDefinitionCodec
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

final class ExperimentMappingSpec extends AnyFunSuite with Matchers:
  test("T2 dtr_coordinates decodes and defaults to empty when absent") {
    val withField: String = "{\"experiment_id\":\"EXP-X\",\"hypothesis_id\":\"H-1\",\"runtime\":\"python\",\"command\":[\"python3\",\"probe.py\"],\"working_directory\":\"..\",\"timeout_seconds\":60,\"test_artifact_version\":\"v1\",\"dtr_coordinates\":[\"TYPE.a.Domain\"]}"
    val withoutField: String = "{\"experiment_id\":\"EXP-Y\",\"hypothesis_id\":\"H-1\",\"runtime\":\"python\",\"command\":[\"python3\",\"probe.py\"],\"working_directory\":\"..\",\"timeout_seconds\":60,\"test_artifact_version\":\"v1\"}"
    val withMapping: ExperimentDefinition = ExperimentDefinitionCodec.parse(withField).toOption.get
    val noMapping: ExperimentDefinition = ExperimentDefinitionCodec.parse(withoutField).toOption.get
    withMapping.dtrCoordinates.map(DtrCoordinate.value) shouldBe Vector("TYPE.a.Domain")
    noMapping.dtrCoordinates shouldBe Vector.empty
  }