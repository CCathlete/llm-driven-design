package dtrbuilder.infrastructure

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.nio.file.{Files, Path}

/** Unit tests for FileSystemWalker with temp directories. */
class FileSystemWalkerSpec extends AnyFlatSpec with Matchers {

  behavior of "FileSystemWalker"

  it should "walk files in a temp directory" in {
    val tempDir = Files.createTempDirectory("dtr-test-")
    try {
      Files.write(tempDir.resolve("test.scala"), "object Hello".getBytes)
      Files.createDirectory(tempDir.resolve("subdir"))
      Files.write(tempDir.resolve("subdir/nested.py"), "print('hello')".getBytes)

      val walker = new FileSystemWalker()
      val entries = walker.walk(tempDir).toSeq

      entries should have size 2
      entries.map(_.relPath) should contain("subdir/nested.py")
    } finally {
      deleteRecursively(tempDir)
    }
  }

  it should "filter out blocklisted files" in {
    val tempDir = Files.createTempDirectory("dtr-test-")
    try {
      Files.write(tempDir.resolve("Main.class"), "binary".getBytes)
      Files.write(tempDir.resolve("Main.scala"), "object Main".getBytes)

      val walker = new FileSystemWalker()
      val entries = walker.walk(tempDir).toSeq

      entries should have size 1
      entries.head.extension shouldBe "scala"
    } finally {
      deleteRecursively(tempDir)
    }
  }

  it should "filter out blocklisted directories" in {
    val tempDir = Files.createTempDirectory("dtr-test-")
    try {
      Files.createDirectory(tempDir.resolve("node_modules"))
      Files.write(tempDir.resolve("node_modules/package.json"), "{}".getBytes)
      Files.write(tempDir.resolve("README.md"), "# Readme".getBytes)

      val walker = new FileSystemWalker()
      val entries = walker.walk(tempDir).toSeq

      entries should have size 1
      entries.head.extension shouldBe "md"
    } finally {
      deleteRecursively(tempDir)
    }
  }

  it should "handle single file root" in {
    val tempDir = Files.createTempDirectory("dtr-test-")
    try {
      val file = tempDir.resolve("single.scala")
      Files.write(file, "object X".getBytes)

      val walker = new FileSystemWalker()
      val entries = walker.walk(file).toSeq

      entries should have size 1
      entries.head.relPath shouldBe "single.scala"
    } finally {
      deleteRecursively(tempDir)
    }
  }

  private def deleteRecursively(path: Path): Unit = {
    if (Files.isDirectory(path)) {
      Files.list(path).forEach(deleteRecursively)
    }
    Files.deleteIfExists(path)
  }
}
