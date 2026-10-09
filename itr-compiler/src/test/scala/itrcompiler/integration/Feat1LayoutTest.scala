package itrcompiler.integration

import itrcompiler.application.ports.DTRLoad
import itrcompiler.domain.models.{ArchCU, CompileCommand, CU, CUBatch, CUSynopsis, LegendCU}
import itrcompiler.infrastructure.parsers.JSONFormat
import org.scalatest.funspec.AnyFunSpec
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

/** Feat-1 T1: per-CU directory layout + criterion-11 backward compatibility. */
class Feat1LayoutTest extends AnyFunSpec {
  private def dtrEmpty: DTRLoad = new DTRLoad {
    def load(path: Path): String = ""
  }

  private def fullComponents(tag: String): Map[String, String] = Map(
    "coordinates" -> s"COORDINATES body $tag",
    "requirements" -> s"REQUIREMENTS body $tag",
    "implementation-steps" -> s"IMPLEMENTATION_STEPS body $tag",
    "acceptance" -> s"ACCEPTANCE body $tag"
  )

  private def validBatch(): CUBatch = CUBatch(
    cus = Seq(
      CU(id = "arch", dtrCoordinates = Seq.empty, content = "Architecture rules", cuType = ArchCU),
      CU(id = "legend", dtrCoordinates = Seq.empty, content = "Legend content", cuType = LegendCU),
      CU(id = "cu-001", dtrCoordinates = Seq("TYPE.A"), content = "", components = fullComponents("cu-001")),
      CU(id = "cu-002", dtrCoordinates = Seq("TYPE.B"), content = "", components = fullComponents("cu-002"))
    ),
    synopsis = Some(CUSynopsis(
      features = Seq("feat-1-per-cu-itr-directories"),
      maxAttempts = 3,
      summary = "Per-CU ITR directories with retry budget 3"
    ))
  )

  private def writeBatchFile(tmpDir: Path, batch: CUBatch): Path = {
    val batchFile = tmpDir.resolve("batch.json")
    Files.write(batchFile, new JSONFormat().serialize(batch).getBytes(StandardCharsets.UTF_8))
    batchFile
  }

  private def read(p: Path): String =
    new String(Files.readAllBytes(p), StandardCharsets.UTF_8)

  it("should emit per-CU folders with four components, root SYNOPSIS/ARCH/LEGEND, no flat blobs (T1)") {
    val tmpDir = Files.createTempDirectory("feat1-layout-")
    val fs = new itrcompiler.infrastructure.filesystem.FileSystem()
    val compile = TestHelper.createCompile(dtrEmpty, fs)
    val outDir = tmpDir.resolve("out")

    val batchFile = writeBatchFile(tmpDir, validBatch())
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
    assert(results.size == 4)

    // Each component-carrying CU holds all four component files
    Seq("cu-001", "cu-002").foreach { id =>
      assert(Files.exists(outDir.resolve(s"$id/COORDINATES.itr")), s"$id COORDINATES.itr")
      assert(Files.exists(outDir.resolve(s"$id/REQUIREMENTS.itr")), s"$id REQUIREMENTS.itr")
      assert(Files.exists(outDir.resolve(s"$id/IMPLEMENTATION_STEPS.itr")), s"$id IMPLEMENTATION_STEPS.itr")
      assert(Files.exists(outDir.resolve(s"$id/ACCEPTANCE.itr")), s"$id ACCEPTANCE.itr")
      // No per-CU .itr blob for component-carrying CUs (neither in folder nor flat at root)
      assert(!Files.exists(outDir.resolve(s"$id/$id.itr")), s"no $id blob in folder")
      assert(!Files.exists(outDir.resolve(s"$id.itr")), s"no flat $id.itr at root")
    }

    // Root ARCH.itr / LEGEND.itr
    assert(Files.exists(outDir.resolve("ARCH.itr")))
    assert(Files.exists(outDir.resolve("LEGEND.itr")))

    // Root SYNOPSIS.itr carries FEATURES_IMPLEMENTED + MAX_ATTEMPTS + non-empty SUMMARY
    val synPath = outDir.resolve("SYNOPSIS.itr")
    assert(Files.exists(synPath))
    val syn = read(synPath)
    assert(syn.contains("FEATURES_IMPLEMENTED="))
    assert(syn.contains("MAX_ATTEMPTS="))
    val summaryLine = syn.split("\n").find(_.startsWith("SUMMARY=")).getOrElse("SUMMARY=")
    assert(summaryLine.stripPrefix("SUMMARY=").trim.nonEmpty)
  }

  it("should keep a legacy component-less batch to one frame inside cu-<id>/ with no root flat (criterion 11)") {
    val tmpDir = Files.createTempDirectory("feat1-legacy-")
    val fs = new itrcompiler.infrastructure.filesystem.FileSystem()
    val compile = TestHelper.createCompile(dtrEmpty, fs)
    val outDir = tmpDir.resolve("out")

    val legacy = CUBatch(Seq(
      CU(id = "arch", dtrCoordinates = Seq.empty, content = "Architecture rules", cuType = ArchCU),
      CU(id = "legend", dtrCoordinates = Seq.empty, content = "Legend content", cuType = LegendCU),
      CU(id = "cu-old", dtrCoordinates = Seq("TYPE.X"), content = "old format content")
    ))
    val batchFile = writeBatchFile(tmpDir, legacy)
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

    // Single frame lands inside cu-<id>/, never flat at root
    assert(Files.exists(outDir.resolve("cu-old/cu-old.itr")))
    assert(!Files.exists(outDir.resolve("cu-old.itr")))
    assert(read(outDir.resolve("cu-old/cu-old.itr")).contains("old format content"))
    // No component files, no SYNOPSIS.itr for a legacy batch
    assert(!Files.exists(outDir.resolve("cu-old/COORDINATES.itr")))
    assert(!Files.exists(outDir.resolve("SYNOPSIS.itr")))
    assert(Files.exists(outDir.resolve("ARCH.itr")))
    assert(Files.exists(outDir.resolve("LEGEND.itr")))
  }
}
