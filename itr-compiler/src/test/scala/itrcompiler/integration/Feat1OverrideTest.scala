package itrcompiler.integration

import itrcompiler.application.ports.DTRLoad
import itrcompiler.domain.models.{ArchCU, CompileCommand, CU, CUBatch, CUSynopsis, LegendCU}
import itrcompiler.infrastructure.parsers.JSONFormat
import org.scalatest.funspec.AnyFunSpec
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

/** Feat-1 T4: Advisor-placed overrides survive recompiles with and without --force. */
class Feat1OverrideTest extends AnyFunSpec {
  private def dtrEmpty: DTRLoad = new DTRLoad {
    def load(path: Path): String = ""
  }

  private def fullComponents(tag: String): Map[String, String] = Map(
    "coordinates" -> s"COORDINATES body $tag",
    "requirements" -> s"REQUIREMENTS body $tag",
    "implementation-steps" -> s"IMPLEMENTATION_STEPS body $tag",
    "acceptance" -> s"ACCEPTANCE body $tag"
  )

  it("should preserve an extra Advisor-placed file byte-identical with and without --force (T4)") {
    val tmpDir = Files.createTempDirectory("feat1-override-")
    val outDir = tmpDir.resolve("out")
    Files.createDirectories(outDir.resolve("cu-001"))

    // Pre-place an extra file in the out folder (Advisor override, not a batch CU)
    val extra = outDir.resolve("cu-001/ADVISOR-NOTE.itr")
    val extraBytes = "ADVISOR OVERRIDE — do not touch\n".getBytes(StandardCharsets.UTF_8)
    Files.write(extra, extraBytes)
    val before = Files.readAllBytes(extra)

    val batch = CUBatch(
      cus = Seq(
        CU(id = "arch", dtrCoordinates = Seq.empty, content = "Architecture rules", cuType = ArchCU),
        CU(id = "legend", dtrCoordinates = Seq.empty, content = "Legend content", cuType = LegendCU),
        CU(id = "cu-001", dtrCoordinates = Seq("TYPE.A"), content = "", components = fullComponents("cu-001"))
      ),
      synopsis = Some(CUSynopsis(
        features = Seq("feat-1-per-cu-itr-directories"),
        maxAttempts = 3,
        summary = "Per-CU ITR directories with retry budget 3"
      ))
    )
    val batchFile = tmpDir.resolve("batch.json")
    Files.write(batchFile, new JSONFormat().serialize(batch).getBytes(StandardCharsets.UTF_8))

    val fs = new itrcompiler.infrastructure.filesystem.FileSystem()

    // Compile without --force: exit 0, extra file byte-identical
    val compileNoForce = TestHelper.createCompile(dtrEmpty, fs)
    val resultsNoForce = compileNoForce.execute(CompileCommand(
      compile = true,
      dtr = None,
      outFolder = outDir,
      rawContent = None,
      cuId = None,
      jsonContent = Some(batchFile),
      yamlContent = None,
      force = false
    ))
    assert(resultsNoForce.size == 3)
    assert(Files.readAllBytes(extra).sameElements(before), "extra file unchanged without --force")

    // Compile with --force: exit 0, extra file still byte-identical
    val compileForce = TestHelper.createCompile(dtrEmpty, fs)
    val resultsForce = compileForce.execute(CompileCommand(
      compile = true,
      dtr = None,
      outFolder = outDir,
      rawContent = None,
      cuId = None,
      jsonContent = Some(batchFile),
      yamlContent = None,
      force = true
    ))
    assert(resultsForce.size == 3)
    assert(Files.readAllBytes(extra).sameElements(before), "extra file unchanged with --force")

    // Sanity: the compiled CU folder itself was still written
    assert(Files.exists(outDir.resolve("cu-001/COORDINATES.itr")))
  }
}
