package itrcompiler.integration

import itrcompiler.application.services.{Compile, ContentDeserialize, CoordinateRules, CUStore}
import itrcompiler.domain.models.CompileCommand
import org.scalatest.funspec.AnyFunSpec
import java.nio.file.{Files, Paths}

class ForceOverwriteTest extends AnyFunSpec {
  it("should overwrite existing CU file when force=true") {
    val tmpDir = Files.createTempDirectory("int-force-")
    val outDir = tmpDir.resolve("out")
    Files.createDirectories(outDir)
    val existing = outDir.resolve("cu-force.itr")
    Files.write(existing, "ORIGINAL".getBytes)

    val fs = new itrcompiler.infrastructure.filesystem.FileSystem()
    val coordRules = new CoordinateRules
    val cuStore = new CUStore(fs)
    val contentDeser = new ContentDeserialize(fs)

    import itrcompiler.application.ports.DTRLoad
    val dtrLoad = new DTRLoad {
      def load(path: java.nio.file.Path): String = ""
    }

    val compile = new Compile(dtrLoad, coordRules, cuStore, contentDeser)

    val cmd = CompileCommand(
      compile = true,
      dtr = None,
      outFolder = outDir,
      rawContent = Some("NEW CONTENT"),
      cuId = Some("cu-force"),
      jsonContent = None,
      yamlContent = None,
      force = true
    )

    compile.execute(cmd)
    val content = new String(Files.readAllBytes(existing))
    assert(content.contains("NEW CONTENT"))
    assert(!content.contains("ORIGINAL"))
  }

  it("should skip existing CU file when force=false") {
    val tmpDir = Files.createTempDirectory("int-skip-")
    val outDir = tmpDir.resolve("out")
    Files.createDirectories(outDir)
    val existing = outDir.resolve("cu-skip.itr")
    Files.write(existing, "ORIGINAL".getBytes)

    val fs = new itrcompiler.infrastructure.filesystem.FileSystem()
    val coordRules = new CoordinateRules
    val cuStore = new CUStore(fs)
    val contentDeser = new ContentDeserialize(fs)

    import itrcompiler.application.ports.DTRLoad
    val dtrLoad = new DTRLoad {
      def load(path: java.nio.file.Path): String = ""
    }

    val compile = new Compile(dtrLoad, coordRules, cuStore, contentDeser)

    val cmd = CompileCommand(
      compile = true,
      dtr = None,
      outFolder = outDir,
      rawContent = Some("NEW CONTENT"),
      cuId = Some("cu-skip"),
      jsonContent = None,
      yamlContent = None,
      force = false
    )

    compile.execute(cmd)
    val content = new String(Files.readAllBytes(existing))
    assert(content == "ORIGINAL") // unchanged
  }
}
