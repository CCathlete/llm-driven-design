package dtrbuilder.infrastructure

import dtrbuilder.domain.models.{DtrChunk, RawEntry}
import dtrbuilder.infrastructure.writer.FileSystemDtrWriter
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.nio.file.Files

/** Unit tests for FileSystemDtrWriter (implements application.DtrWriter port). */
class FileSystemDtrWriterSpec extends AnyFlatSpec with Matchers {

  private val writer = new FileSystemDtrWriter

  behavior of "FileSystemDtrWriter"

  it should "write a single chunk directly without -NNN suffix" in {
    val tempDir = Files.createTempDirectory("dtr-writer-test-")
    val outputPath = tempDir.resolve("output.dtr")
    val chunks = Seq(
      DtrChunk(Seq(RawEntry("A=1")), 4, 0, 1)
    )
    val written = writer.write(chunks, outputPath)
    try {
      written should have size 1
      written.head shouldBe outputPath
      Files.exists(outputPath) shouldBe true
    } finally {
      Files.deleteIfExists(outputPath)
      Files.deleteIfExists(tempDir)
    }
  }

  it should "write multi-chunk output with -NNN suffix" in {
    val tempDir = Files.createTempDirectory("dtr-writer-chunk-")
    val outputPath = tempDir.resolve("output.dtr")
    val chunks = Seq(
      DtrChunk(Seq(RawEntry("A=1")), 4, 0, 3),
      DtrChunk(Seq(RawEntry("B=2")), 4, 1, 3),
      DtrChunk(Seq(RawEntry("C=3")), 4, 2, 3)
    )
    val written = writer.write(chunks, outputPath)
    try {
      written should have size 3
      written.map(_.getFileName.toString) should contain allOf("output-001.dtr", "output-002.dtr", "output-003.dtr")
      written.foreach(p => Files.exists(p) shouldBe true)
    } finally {
      written.foreach(p => Files.deleteIfExists(p))
      Files.deleteIfExists(tempDir)
    }
  }

  it should "write empty chunk list as empty" in {
    val tempDir = Files.createTempDirectory("dtr-writer-empty-")
    val outputPath = tempDir.resolve("empty.dtr")
    try {
      val written = writer.write(Seq.empty, outputPath)
      written shouldBe empty
      Files.exists(outputPath) shouldBe false
    } finally {
      Files.deleteIfExists(tempDir)
    }
  }

  it should "handle paths without extension for chunked output" in {
    val tempDir = Files.createTempDirectory("dtr-writer-noext-")
    val outputPath = tempDir.resolve("output")
    val chunks = Seq(
      DtrChunk(Seq(RawEntry("X=1")), 4, 0, 2),
      DtrChunk(Seq(RawEntry("Y=2")), 4, 1, 2)
    )
    val written = writer.write(chunks, outputPath)
    try {
      written should have size 2
      written.map(_.getFileName.toString) should contain allOf("output-001", "output-002")
    } finally {
      written.foreach(p => Files.deleteIfExists(p))
      Files.deleteIfExists(tempDir)
    }
  }

  it should "write correct content to output file" in {
    val tempDir = Files.createTempDirectory("dtr-writer-content-")
    val outputPath = tempDir.resolve("test.dtr")
    val chunks = Seq(
      DtrChunk(Seq(RawEntry("FILE=test.scala")), 4, 0, 1)
    )
    val written = writer.write(chunks, outputPath)
    try {
      val content = new String(Files.readAllBytes(outputPath))
      content should include("FILE=test.scala")
      content should endWith("\n")
    } finally {
      Files.deleteIfExists(outputPath)
      Files.deleteIfExists(tempDir)
    }
  }

  it should "create intermediate directories" in {
    val tempDir = Files.createTempDirectory("dtr-writer-nested-")
    val nestedPath = tempDir.resolve("subdir").resolve("deep").resolve("out.dtr")
    val chunks = Seq(
      DtrChunk(Seq(RawEntry("Z=9")), 4, 0, 1)
    )
    val written = writer.write(chunks, nestedPath)
    try {
      written should have size 1
      Files.exists(nestedPath) shouldBe true
    } finally {
      Files.deleteIfExists(nestedPath)
      Files.deleteIfExists(nestedPath.getParent)
      Files.deleteIfExists(nestedPath.getParent.getParent)
      Files.deleteIfExists(tempDir)
    }
  }
}
