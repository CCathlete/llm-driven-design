package itrcompiler.integration

import itrcompiler.application.services.{Compile, ContentDeserialize, CoordinateRules, CUStore, RequiredPartsValidation}
import itrcompiler.application.ports.DTRLoad
import itrcompiler.domain.models.CompileCommand
import org.scalatest.funspec.AnyFunSpec
import java.nio.file.{Files, Paths}

class BatchJSONWriteTest extends AnyFunSpec {
  it("should compile a JSON batch and write multiple CUs") {
    val tmpDir = Files.createTempDirectory("int-json-")

    val fs = new itrcompiler.infrastructure.filesystem.FileSystem()
    val coordRules = new CoordinateRules
    val cuStore = new CUStore(fs)
    val contentDeser = new ContentDeserialize(fs)
    val requiredPartsValidation = new RequiredPartsValidation

    val dtrLoad = new DTRLoad {
      def load(path: java.nio.file.Path): String = ""
    }

    val compile = new Compile(dtrLoad, coordRules, cuStore, contentDeser, requiredPartsValidation)

    // Write a JSON batch file with required parts
    val jsonFile = tmpDir.resolve("batch.json")
    val json = """[{"cu-id":"arch","cu-type":"arch","dtr-coordinates":[],"content":"Architecture rules"},{"cu-id":"legend","cu-type":"legend","dtr-coordinates":[],"content":"Legend content"},{"cu-id":"cu-a","dtr-coordinates":["TYPE.A"],"content":"content a"},{"cu-id":"cu-b","dtr-coordinates":["TYPE.B"],"content":"content b"}]"""
    Files.write(jsonFile, json.getBytes)

    val cmd = CompileCommand(
      compile = true,
      dtr = None,
      outFolder = tmpDir.resolve("out"),
      rawContent = None,
      cuId = None,
      jsonContent = Some(jsonFile),
      yamlContent = None,
      force = false
    )

    val results = compile.execute(cmd)
    assert(results.size == 4)
    assert(Files.exists(tmpDir.resolve("out/ARCH.itr")))
    assert(Files.exists(tmpDir.resolve("out/LEGEND.itr")))
    assert(Files.exists(tmpDir.resolve("out/cu-a.itr")))
    assert(Files.exists(tmpDir.resolve("out/cu-b.itr")))

    val contentA = new String(Files.readAllBytes(tmpDir.resolve("out/cu-a.itr")))
    assert(contentA.contains("content a"))
  }
}
