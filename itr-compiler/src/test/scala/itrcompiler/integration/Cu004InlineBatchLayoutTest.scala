package itrcompiler.integration

import itrcompiler.application.ports.DTRLoad
import itrcompiler.domain.models.CompileCommand
import org.scalatest.funspec.AnyFunSpec
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

/** cu-004: end-to-end regression — an inline-section batch JSON string
  * compiled through the real batch-file path (Compile + FileSystem)
  * must yield per-CU four-file folders with no blobs.
  *
  * Mirrors Feat1LayoutTest but starts from a raw batch JSON string
  * (inline REQUIREMENTS:/COORDINATES:/IMPLEMENTATION_STEPS:/ACCEPTANCE:
  * sections in `content`) rather than directly constructed CUs.
  */
class Cu004InlineBatchLayoutTest extends AnyFunSpec {
  private def dtrEmpty: DTRLoad = new DTRLoad {
    def load(path: Path): String = ""
  }

  private def read(p: Path): String =
    new String(Files.readAllBytes(p), StandardCharsets.UTF_8)

  it("should compile an inline-section batch string into per-CU four-file folders with no blobs (cu-004)") {
    val tmpDir = Files.createTempDirectory("cu004-inline-")
    val fs = new itrcompiler.infrastructure.filesystem.FileSystem()
    val compile = TestHelper.createCompile(dtrEmpty, fs)
    val outDir = tmpDir.resolve("out")

    // Raw batch JSON string: arch + legend + one regular CU whose four
    // sections live inline in `content` (the splitter path, not an
    // explicit "components" object). Written to a real batch file.
    val inlineContent = "REQUIREMENTS: req body cu-001\\nCOORDINATES: coord body cu-001\\nIMPLEMENTATION_STEPS: steps body cu-001\\nACCEPTANCE: acc body cu-001"
    val batchJson =
      s"""[{"cu-id":"arch","cu-type":"arch","dtr-coordinates":[],"content":"arch rules"},""" +
        s"""{"cu-id":"legend","cu-type":"legend","dtr-coordinates":[],"content":"legend content"},""" +
        s"""{"cu-id":"cu-001","dtr-coordinates":["TYPE.A"],"content":"$inlineContent"}]"""
    val batchFile = tmpDir.resolve("batch.json")
    Files.write(batchFile, batchJson.getBytes(StandardCharsets.UTF_8))

    val cmd = CompileCommand(
      compile = true,
      dtr = None,
      outFolder = outDir,
      rawContent = None,
      cuId = None,
      jsonContent = Some(batchFile),
      yamlContent = None,
      force = false
    )
    val results = compile.execute(cmd)
    assert(results.size == 3)

    // Per-CU four-file folder for the inline-section CU
    assert(Files.exists(outDir.resolve("cu-001/COORDINATES.itr")), "cu-001 COORDINATES.itr")
    assert(Files.exists(outDir.resolve("cu-001/REQUIREMENTS.itr")), "cu-001 REQUIREMENTS.itr")
    assert(Files.exists(outDir.resolve("cu-001/IMPLEMENTATION_STEPS.itr")), "cu-001 IMPLEMENTATION_STEPS.itr")
    assert(Files.exists(outDir.resolve("cu-001/ACCEPTANCE.itr")), "cu-001 ACCEPTANCE.itr")

    // Bodies survive the split -> write round-trip
    assert(read(outDir.resolve("cu-001/REQUIREMENTS.itr")).contains("req body cu-001"))
    assert(read(outDir.resolve("cu-001/COORDINATES.itr")).contains("coord body cu-001"))
    assert(read(outDir.resolve("cu-001/IMPLEMENTATION_STEPS.itr")).contains("steps body cu-001"))
    assert(read(outDir.resolve("cu-001/ACCEPTANCE.itr")).contains("acc body cu-001"))

    // No blobs: neither in the folder nor flat at the root
    assert(!Files.exists(outDir.resolve("cu-001/cu-001.itr")), "no cu-001 blob in folder")
    assert(!Files.exists(outDir.resolve("cu-001.itr")), "no flat cu-001.itr at root")

    // Root ARCH.itr / LEGEND.itr still emitted
    assert(Files.exists(outDir.resolve("ARCH.itr")))
    assert(Files.exists(outDir.resolve("LEGEND.itr")))
  }
}
