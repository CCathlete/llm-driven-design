package itrcompiler.application.services

import itrcompiler.application.ports.{ContentRead, CUWrite, DTRLoad}
import itrcompiler.domain.models._
import org.scalatest.funspec.AnyFunSpec
import java.nio.file.Paths

class CompileTest extends AnyFunSpec {
  def fixture: Compile = {
    val dtrLoad = new DTRLoad {
      def load(path: java.nio.file.Path): String = "TYPE.Foo\nFILE.Bar"
    }
    val coordRules = new CoordinateRules
    val cuWrite = new CUWrite {
      def write(cu: CU, outFolder: java.nio.file.Path, force: Boolean): Unit = ()
    }
    val contentRead = new ContentRead {
      def readJson(path: java.nio.file.Path) = CUBatch(Seq(
        CU(id = "arch", dtrCoordinates = Seq.empty, content = "arch content", cuType = ArchCU),
        CU(id = "legend", dtrCoordinates = Seq.empty, content = "legend content", cuType = LegendCU),
        CU(id = "verification", dtrCoordinates = Seq.empty, content = "verification content", cuType = VerificationCU),
        CU(id = "e2everification", dtrCoordinates = Seq.empty, content = "e2e content", cuType = E2eVerificationCU),
        CU(id = "cu-json", dtrCoordinates = Seq("TYPE.Json"), content = "from json")
      ))
      def readYaml(path: java.nio.file.Path) = CUBatch(Seq(
        CU(id = "arch", dtrCoordinates = Seq.empty, content = "arch content", cuType = ArchCU),
        CU(id = "legend", dtrCoordinates = Seq.empty, content = "legend content", cuType = LegendCU),
        CU(id = "verification", dtrCoordinates = Seq.empty, content = "verification content", cuType = VerificationCU),
        CU(id = "e2everification", dtrCoordinates = Seq.empty, content = "e2e content", cuType = E2eVerificationCU),
        CU(id = "cu-yaml", dtrCoordinates = Seq("TYPE.Yaml"), content = "from yaml")
      ))
    }
    val cuStore = new CUStore(cuWrite)
    val contentDeser = new ContentDeserialize(contentRead)
    val requiredPartsValidation = new RequiredPartsValidation
    new Compile(dtrLoad, coordRules, cuStore, contentDeser, requiredPartsValidation)
  }

  describe("Compile") {
    it("should compile a single CU from raw content") {
      val compile = fixture
      val cmd = CompileCommand(
        compile = true,
        dtr = Some(Paths.get("test.dtr")),
        outFolder = Paths.get("out"),
        rawContent = Some("hello world"),
        cuId = Some("cu-raw"),
        jsonContent = None,
        yamlContent = None,
        force = false
      )
      val results = compile.execute(cmd)
      assert(results.size == 1)
      assert(results.head.id == "cu-raw")
      assert(results.head.content == "hello world")
    }

    it("should compile a JSON batch with required parts") {
      val compile = fixture
      val cmd = CompileCommand(
        compile = true,
        dtr = None,
        outFolder = Paths.get("out"),
        rawContent = None,
        cuId = None,
        jsonContent = Some(Paths.get("batch.json")),
        yamlContent = None,
        force = false
      )
      val results = compile.execute(cmd)
      assert(results.size == 5)
      assert(results.exists(_.id == "cu-json"))
      assert(results.exists(_.cuType == ArchCU))
      assert(results.exists(_.cuType == LegendCU))
      assert(results.exists(_.cuType == VerificationCU))
      assert(results.exists(_.cuType == E2eVerificationCU))
    }

    it("should compile a YAML batch with required parts") {
      val compile = fixture
      val cmd = CompileCommand(
        compile = true,
        dtr = None,
        outFolder = Paths.get("out"),
        rawContent = None,
        cuId = None,
        jsonContent = None,
        yamlContent = Some(Paths.get("batch.yaml")),
        force = false
      )
      val results = compile.execute(cmd)
      assert(results.size == 5)
      assert(results.exists(_.id == "cu-yaml"))
      assert(results.exists(_.cuType == ArchCU))
      assert(results.exists(_.cuType == LegendCU))
      assert(results.exists(_.cuType == VerificationCU))
      assert(results.exists(_.cuType == E2eVerificationCU))
    }

    it("should handle empty input gracefully") {
      val compile = fixture
      val cmd = CompileCommand(
        compile = true,
        dtr = None,
        outFolder = Paths.get("out"),
        rawContent = None,
        cuId = None,
        jsonContent = None,
        yamlContent = None,
        force = false
      )
      val results = compile.execute(cmd)
      assert(results.isEmpty)
    }

    it("should load DTR coordinates and validate them") {
      val compile = fixture
      val cmd = CompileCommand(
        compile = true,
        dtr = Some(Paths.get("test.dtr")),
        outFolder = Paths.get("out"),
        rawContent = Some("data"),
        cuId = Some("cu-dtr"),
        jsonContent = None,
        yamlContent = None,
        force = false
      )
      val results = compile.execute(cmd)
      assert(results.head.dtrCoordinates == Seq("TYPE.Foo", "FILE.Bar"))
    }
  }
}
