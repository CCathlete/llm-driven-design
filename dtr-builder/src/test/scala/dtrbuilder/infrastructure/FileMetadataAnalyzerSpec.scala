package dtrbuilder.infrastructure

import dtrbuilder.domain.FileEntry
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.nio.file.{Files}

/** Unit tests for FileMetadataAnalyzer. */
class FileMetadataAnalyzerSpec extends AnyFlatSpec with Matchers {

  private val analyzer = new FileMetadataAnalyzer

  behavior of "FileMetadataAnalyzer"

  it should "detect MIME type for Scala files" in {
    val tempFile = Files.createTempFile("test-", ".scala")
    try {
      Files.write(tempFile, "object Test".getBytes)
      val entry = FileEntry(
        relPath = "Test.scala",
        absolutePath = tempFile,
        byteSize = 0,
        mimeType = "",
        encoding = "",
        language = None,
        extension = "scala",
        isDirectory = false
      )

      val enriched = analyzer.analyze(entry)
      enriched.mimeType should include("scala")
      enriched.byteSize should be > 0L
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  it should "detect correct byte size" in {
    val tempFile = Files.createTempFile("test-", ".txt")
    try {
      val content = "Hello, World!"
      Files.write(tempFile, content.getBytes)
      val entry = FileEntry(
        relPath = "test.txt",
        absolutePath = tempFile,
        byteSize = 0,
        mimeType = "",
        encoding = "",
        language = None,
        extension = "txt",
        isDirectory = false
      )

      val enriched = analyzer.analyze(entry)
      enriched.byteSize shouldBe content.getBytes.length.toLong
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  it should "detect UTF-8 encoding" in {
    val tempFile = Files.createTempFile("test-", ".txt")
    try {
      Files.write(tempFile, "UTF-8 content: ñoño".getBytes(java.nio.charset.StandardCharsets.UTF_8))
      val entry = FileEntry(
        relPath = "test.txt",
        absolutePath = tempFile,
        byteSize = 0,
        mimeType = "",
        encoding = "",
        language = None,
        extension = "txt",
        isDirectory = false
      )

      val enriched = analyzer.analyze(entry)
      enriched.encoding shouldBe "UTF-8"
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }
}
