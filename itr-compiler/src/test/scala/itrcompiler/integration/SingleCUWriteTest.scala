package itrcompiler.integration

import itrcompiler.application.ports.{ContentRead, CUWrite, DTRLoad}
import itrcompiler.application.services.{Compile, ContentDeserialize, CoordinateRules, CUStore}
import itrcompiler.domain.models.{CU, CUBatch, CompileCommand}
import org.scalatest.funspec.AnyFunSpec
import java.nio.file.{Files, Path, Paths}

class SingleCUWriteTest extends AnyFunSpec {
  it("should compile a single CU and write it to disk") {
    val tmpDir = Files.createTempDirectory("int-single-")

    // Real FileSystem for writing
    val fs = new itrcompiler.infrastructure.filesystem.FileSystem()
    val coordRules = new CoordinateRules
    val cuStore = new CUStore(fs)
    val contentDeser = new ContentDeserialize(fs)

    // DTRLoad that returns fake DTR content
    val dtrLoad = new DTRLoad {
      def load(path: Path): String = "TYPE.Foo\nFILE.Bar\n"
    }

    val compile = new Compile(dtrLoad, coordRules, cuStore, contentDeser)

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
