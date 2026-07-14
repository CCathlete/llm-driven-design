package itrcompiler.integration

import itrcompiler.application.services.{Compile, ContentDeserialize, CoordinateRules, CUStore, RequiredPartsValidation}
import itrcompiler.application.ports.DTRLoad
import itrcompiler.domain.models.{CU, CompileCommand}
import org.scalatest.funspec.AnyFunSpec
import java.nio.file.{Files, Paths}

class BatchYAMLWriteTest extends AnyFunSpec {
  it("should compile a YAML batch and write multiple CUs") {
    val tmpDir = Files.createTempDirectory("int-yaml-")

    val fs = new itrcompiler.infrastructure.filesystem.FileSystem()
    val coordRules = new CoordinateRules
    val cuStore = new CUStore(fs)
    val contentDeser = new ContentDeserialize(fs)
    val requiredPartsValidation = new RequiredPartsValidation

    val dtrLoad = new DTRLoad {
      def load(path: java.nio.file.Path): String = ""
    }

    val compile = new Compile(dtrLoad, coordRules, cuStore, contentDeser, requiredPartsValidation)

    // Write a YAML batch file with required parts
    val yamlFile = tmpDir.resolve("batch.yaml")
    val yaml = """arch:
                 |  cu-type: arch
                 |  dtr-coordinates: []
                 |  content: "Architecture rules"
                 |
                 |legend:
                 |  cu-type: legend
                 |  dtr-coordinates: []
                 |  content: "Legend content"
                 |
                 |verification:
                 |  cu-type: verification
                 |  dtr-coordinates: []
                 |  content: "Verification tests"
                 |
                 |e2everification:
                 |  cu-type: e2everification
                 |  dtr-coordinates: []
                 |  content: "E2E verification tests"
                 |
                 |cu-x:
                 |  dtr-coordinates: [TYPE.X]
                 |  content: "content x"
                 |
                 |cu-y:
                 |  dtr-coordinates: [TYPE.Y]
                 |  content: "content y"
                 |""".stripMargin
    Files.write(yamlFile, yaml.getBytes)

    val cmd = CompileCommand(
      compile = true,
      dtr = None,
      outFolder = tmpDir.resolve("out"),
      rawContent = None,
      cuId = None,
      jsonContent = None,
      yamlContent = Some(yamlFile),
      force = false
    )

    val results = compile.execute(cmd)
    assert(results.size == 6)
    assert(Files.exists(tmpDir.resolve("out/ARCH.itr")))
    assert(Files.exists(tmpDir.resolve("out/LEGEND.itr")))
    assert(Files.exists(tmpDir.resolve("out/verification.verification.itr")))
    assert(Files.exists(tmpDir.resolve("out/E2EVERIFICATION.itr")))
    assert(Files.exists(tmpDir.resolve("out/cu-x.itr")))
    assert(Files.exists(tmpDir.resolve("out/cu-y.itr")))

    val contentX = new String(Files.readAllBytes(tmpDir.resolve("out/cu-x.itr")))
    assert(contentX.contains("content x"))
  }
}
