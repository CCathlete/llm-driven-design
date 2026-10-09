package itrcompiler.integration

import itrcompiler.application.ports.DTRLoad
import itrcompiler.domain.models.{ArchCU, CompileCommand, CU, CUBatch, CUSynopsis, LegendCU}
import itrcompiler.infrastructure.parsers.JSONFormat
import org.scalatest.funspec.AnyFunSpec
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

/** Feat-1 T2/T3/T7 enforcement + T6a creation semantics. */
class Feat1ValidationTest extends AnyFunSpec {
  private def dtrEmpty: DTRLoad = new DTRLoad {
    def load(path: Path): String = ""
  }

  private def fullComponents(tag: String): Map[String, String] = Map(
    "coordinates" -> s"COORDINATES body $tag",
    "requirements" -> s"REQUIREMENTS body $tag",
    "implementation-steps" -> s"IMPLEMENTATION_STEPS body $tag",
    "acceptance" -> s"ACCEPTANCE body $tag"
  )

  private def goodSynopsis: CUSynopsis = CUSynopsis(
    features = Seq("feat-1-per-cu-itr-directories"),
    maxAttempts = 3,
    summary = "Per-CU ITR directories with retry budget 3"
  )

  private def writeBatchFile(tmpDir: Path, batch: CUBatch): Path = {
    val batchFile = tmpDir.resolve("batch.json")
    Files.write(batchFile, new JSONFormat().serialize(batch).getBytes(StandardCharsets.UTF_8))
    batchFile
  }

  private def compileBatch(tmpDir: Path, batchFile: Path, outDir: Path) = {
    val fs = new itrcompiler.infrastructure.filesystem.FileSystem()
    val compile = TestHelper.createCompile(dtrEmpty, fs)
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
    compile.execute(cmd)
  }

  it("should fail naming CU id and missing component when a component is omitted (T2)") {
    val tmpDir = Files.createTempDirectory("feat1-val-comp-")
    val outDir = tmpDir.resolve("out")
    val batch = CUBatch(
      cus = Seq(
        CU(id = "arch", dtrCoordinates = Seq.empty, content = "Architecture rules", cuType = ArchCU),
        CU(id = "legend", dtrCoordinates = Seq.empty, content = "Legend content", cuType = LegendCU),
        CU(
          id = "cu-x",
          dtrCoordinates = Seq("TYPE.X"),
          content = "",
          components = fullComponents("cu-x") - "implementation-steps"
        )
      ),
      synopsis = Some(goodSynopsis)
    )
    val batchFile = writeBatchFile(tmpDir, batch)
    val ex = intercept[IllegalStateException] {
      compileBatch(tmpDir, batchFile, outDir)
    }
    assert(ex.getMessage.contains("cu-x"), "message names the offending CU id")
    assert(ex.getMessage.contains("implementation-steps"), "message names the missing component")
    // Fail-fast before writes: no output files were written
    assert(!Files.exists(outDir), "no output folder on validation failure")
  }

  it("should fail naming MAX_ATTEMPTS when synopsis is absent (T3)") {
    val tmpDir = Files.createTempDirectory("feat1-val-syn-")
    val outDir = tmpDir.resolve("out")
    val batch = CUBatch(
      cus = Seq(
        CU(id = "arch", dtrCoordinates = Seq.empty, content = "Architecture rules", cuType = ArchCU),
        CU(id = "legend", dtrCoordinates = Seq.empty, content = "Legend content", cuType = LegendCU),
        CU(id = "cu-x", dtrCoordinates = Seq("TYPE.X"), content = "", components = fullComponents("cu-x"))
      ),
      synopsis = None
    )
    val batchFile = writeBatchFile(tmpDir, batch)
    val ex = intercept[IllegalStateException] {
      compileBatch(tmpDir, batchFile, outDir)
    }
    assert(ex.getMessage.contains("MAX_ATTEMPTS"), "message names MAX_ATTEMPTS")
    assert(!Files.exists(outDir), "no output folder on validation failure")
  }

  it("should fail naming summary when the synopsis summary is blank (T7)") {
    val tmpDir = Files.createTempDirectory("feat1-val-sum-")
    val outDir = tmpDir.resolve("out")
    val batch = CUBatch(
      cus = Seq(
        CU(id = "arch", dtrCoordinates = Seq.empty, content = "Architecture rules", cuType = ArchCU),
        CU(id = "legend", dtrCoordinates = Seq.empty, content = "Legend content", cuType = LegendCU),
        CU(id = "cu-x", dtrCoordinates = Seq("TYPE.X"), content = "", components = fullComponents("cu-x"))
      ),
      synopsis = Some(goodSynopsis.copy(summary = "   "))
    )
    val batchFile = writeBatchFile(tmpDir, batch)
    val ex = intercept[IllegalStateException] {
      compileBatch(tmpDir, batchFile, outDir)
    }
    assert(ex.getMessage.toLowerCase.contains("summary"), "message names summary")
    assert(!Files.exists(outDir), "no output folder on validation failure")
  }

  it("should treat nonexistent coordinates as creation, never an error (T6a)") {
    val tmpDir = Files.createTempDirectory("feat1-val-create-")
    val fs = new itrcompiler.infrastructure.filesystem.FileSystem()
    val compile = TestHelper.createCompile(dtrEmpty, fs)
    val outDir = tmpDir.resolve("out")
    val batch = CUBatch(
      cus = Seq(
        CU(id = "arch", dtrCoordinates = Seq.empty, content = "Architecture rules", cuType = ArchCU),
        CU(id = "legend", dtrCoordinates = Seq.empty, content = "Legend content", cuType = LegendCU),
        CU(
          id = "cu-new",
          dtrCoordinates = Seq("NOPE.DoesNotExist"),
          content = "",
          components = fullComponents("cu-new")
        )
      ),
      synopsis = Some(goodSynopsis)
    )
    val batchFile = writeBatchFile(tmpDir, batch)
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
    assert(results.exists(_.id == "cu-new"))
    assert(Files.exists(outDir.resolve("cu-new/COORDINATES.itr")))
  }
}
