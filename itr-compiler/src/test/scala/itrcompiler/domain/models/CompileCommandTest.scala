package itrcompiler.domain.models

import org.scalatest.funspec.AnyFunSpec
import java.nio.file.Paths

class CompileCommandTest extends AnyFunSpec {
  describe("CompileCommand") {
    it("should create a single-CU command") {
      val cmd = CompileCommand(
        compile = true,
        dtr = None,
        outFolder = Paths.get("out"),
        rawContent = Some("some content"),
        cuId = Some("cu-001"),
        jsonContent = None,
        yamlContent = None,
        force = false
      )
      assert(cmd.compile)
      assert(cmd.rawContent.contains("some content"))
      assert(cmd.cuId.contains("cu-001"))
    }

    it("should create a JSON batch command") {
      val cmd = CompileCommand(
        compile = true,
        dtr = Some(Paths.get("input.dtr")),
        outFolder = Paths.get("out"),
        rawContent = None,
        cuId = None,
        jsonContent = Some(Paths.get("batch.json")),
        yamlContent = None,
        force = true
      )
      assert(cmd.force)
      assert(cmd.jsonContent.contains(Paths.get("batch.json")))
    }

    it("should create a YAML batch command") {
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
      assert(cmd.yamlContent.contains(Paths.get("batch.yaml")))
    }

    it("should be a Model") {
      val cmd = CompileCommand(
        compile = false,
        dtr = None,
        outFolder = Paths.get("out"),
        rawContent = None,
        cuId = None,
        jsonContent = None,
        yamlContent = None,
        force = false
      )
      assert(cmd.isInstanceOf[Model])
    }
  }
}
