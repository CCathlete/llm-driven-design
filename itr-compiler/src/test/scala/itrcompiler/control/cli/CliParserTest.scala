package itrcompiler.control.cli

import org.scalatest.funspec.AnyFunSpec
import java.nio.file.Paths

class CliParserTest extends AnyFunSpec {
  describe("CliParser") {
    it("should parse a single-CU command") {
      val args = Array("--compile", "--dtr", "input.dtr", "--out-folder", "out", "--raw-content", "hello", "--cu-id", "cu-001")
      val cmd = CliParser.parse(args)
      assert(cmd.isDefined)
      assert(cmd.get.compile)
      assert(cmd.get.dtr.contains(Paths.get("input.dtr")))
      assert(cmd.get.outFolder == Paths.get("out"))
      assert(cmd.get.rawContent.contains("hello"))
      assert(cmd.get.cuId.contains("cu-001"))
    }

    it("should parse a JSON batch command") {
      val args = Array("--compile", "--dtr", "input.dtr", "--out-folder", "out", "--json-content", "batch.json", "--force")
      val cmd = CliParser.parse(args)
      assert(cmd.isDefined)
      assert(cmd.get.jsonContent.contains(Paths.get("batch.json")))
      assert(cmd.get.force)
    }

    it("should parse a YAML batch command") {
      val args = Array("--compile", "--out-folder", "out", "--yaml-content", "batch.yaml")
      val cmd = CliParser.parse(args)
      assert(cmd.isDefined)
      assert(cmd.get.yamlContent.contains(Paths.get("batch.yaml")))
      assert(cmd.get.dtr.isEmpty)
    }

    it("should default out-folder to 'out'") {
      val args = Array("--compile", "--raw-content", "x", "--cu-id", "c")
      val cmd = CliParser.parse(args)
      assert(cmd.get.outFolder == Paths.get("out"))
    }

    it("should return None for empty args") {
      assert(CliParser.parse(Array.empty).isEmpty)
    }

    it("should not set force when absent") {
      val args = Array("--compile")
      val cmd = CliParser.parse(args)
      assert(!cmd.get.force)
    }
  }
}
