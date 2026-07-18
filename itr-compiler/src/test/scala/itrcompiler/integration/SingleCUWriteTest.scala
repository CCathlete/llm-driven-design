package itrcompiler.integration

import itrcompiler.application.ports.DTRLoad
import itrcompiler.application.services.{Compile, ContentDeserialize, CoordinateRules, CUStore, RequiredPartsValidation}
import itrcompiler.domain.models.CompileCommand
import org.scalatest.funspec.AnyFunSpec
import java.nio.file.{Files, Path, Paths}

class SingleCUWriteTest extends AnyFunSpec {
  it("should compile a single CU and write it to disk") {
    val tmpDir = Files.createTempDirectory("int-single-")

    // Pre-create required parts so validation passes
    Files.write(tmpDir.resolve("ARCH.itr"), "ARCH".getBytes)
    Files.write(tmpDir.resolve("LEGEND.itr"), "LEGEND".getBytes)
    Files.write(tmpDir.resolve("verification.verification.itr"), "VERIFICATION".getBytes)
    Files.write(tmpDir.resolve("E2EVERIFICATION.itr"), "E2E".getBytes)

    val fs = new itrcompiler.infrastructure.filesystem.FileSystem()
    val coordRules = new CoordinateRules
    val cuStore = new CUStore(fs)
    val contentDeser = new ContentDeserialize(fs)
    val requiredPartsValidation = new RequiredPartsValidation

    val dtrLoad = new DTRLoad {
      def load(path: Path): String = "TYPE.Foo\nFILE.Bar\n"
    }

    val compile = new Compile(dtrLoad, coordRules, cuStore, contentDeser, requiredPartsValidation)

    val cmd = CompileCommand(
      compile = true,
      dtr = Some(Paths.get("dummy.dtr")),
      outFolder = tmpDir,
      rawContent = Some("def hello(): Unit = println(\"hello\")"),
      cuId = Some("cu-hello"),
      jsonContent = None,
      yamlContent = None,
      force = false
    )

    val results = compile.execute(cmd)
    assert(results.size == 1)

    val written = new String(Files.readAllBytes(tmpDir.resolve("cu-hello.itr")))
    assert(written.contains("# CU-ID: cu-hello"))
    assert(written.contains("# DTR-COORDINATES: TYPE.Foo, FILE.Bar"))
    assert(written.contains("hello"))
  }
}
