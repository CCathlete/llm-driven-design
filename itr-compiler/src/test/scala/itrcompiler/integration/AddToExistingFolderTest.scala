package itrcompiler.integration

import itrcompiler.application.services.{Compile, ContentDeserialize, CoordinateRules, CUStore, RequiredPartsValidation}
import itrcompiler.application.ports.DTRLoad
import itrcompiler.domain.models.CompileCommand
import org.scalatest.funspec.AnyFunSpec
import java.nio.file.{Files, Paths}

class AddToExistingFolderTest extends AnyFunSpec {
  it("should add a new CU to an existing folder without disturbing existing files") {
    val tmpDir = Files.createTempDirectory("int-add-")
    val outDir = tmpDir.resolve("out")
    Files.createDirectories(outDir)

    // Pre-create required part files
    Files.write(outDir.resolve("ARCH.itr"), "ARCH".getBytes)
    Files.write(outDir.resolve("LEGEND.itr"), "LEGEND".getBytes)
    Files.write(outDir.resolve("verification.verification.itr"), "VERIFICATION".getBytes)
    Files.write(outDir.resolve("E2EVERIFICATION.itr"), "E2E".getBytes)

    // Pre-create a CU file
    val existingFile = outDir.resolve("cu-existing.itr")
    Files.write(existingFile, "EXISTING".getBytes)

    val fs = new itrcompiler.infrastructure.filesystem.FileSystem()
    val coordRules = new CoordinateRules
    val cuStore = new CUStore(fs)
    val contentDeser = new ContentDeserialize(fs)
    val requiredPartsValidation = new RequiredPartsValidation

    val dtrLoad = new DTRLoad {
      def load(path: java.nio.file.Path): String = ""
    }

    val compile = new Compile(dtrLoad, coordRules, cuStore, contentDeser, requiredPartsValidation)

    val cmd = CompileCommand(
      compile = true,
      dtr = None,
      outFolder = outDir,
      rawContent = Some("new cu content"),
      cuId = Some("cu-added"),
      jsonContent = None,
      yamlContent = None,
      force = false
    )

    compile.execute(cmd)

    // Existing file unchanged
    assert(new String(Files.readAllBytes(existingFile)) == "EXISTING")
    // New file created
    assert(Files.exists(outDir.resolve("cu-added.itr")))
    val added = new String(Files.readAllBytes(outDir.resolve("cu-added.itr")))
    assert(added.contains("new cu content"))
  }
}
