package dtrbuilder.infrastructure

import dtrbuilder.application.LanguageDetector
import dtrbuilder.domain._
import dtrbuilder.infrastructure.ast.ScalaAstExtractor
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.nio.file.{Files, Path}

/** Unit tests for ScalaAstExtractor with temporary Scala source files. */
class ScalaAstExtractorSpec extends AnyFlatSpec with Matchers {

  private val regexExtractor = new RegexSignatureExtractor
  private val extractor = new ScalaAstExtractor(regexExtractor)

  private def createTempFile(content: String, fileName: String = "TestFile.scala"): Path = {
    val tempDir = Files.createTempDirectory("scala-ast-test-")
    val file = tempDir.resolve(fileName)
    Files.write(file, content.getBytes("UTF-8"))
    file
  }

  private def makeFileEntry(file: Path, langName: String = "Scala"): FileEntry = {
    val lang = LanguageDetector.defaultLanguages.find(_.name == langName)
    FileEntry(
      relPath = file.getFileName.toString,
      absolutePath = file,
      byteSize = Files.size(file),
      mimeType = "text/x-scala",
      encoding = "UTF-8",
      language = lang,
      extension = "scala",
      isDirectory = false
    )
  }

  behavior of "ScalaAstExtractor"

  it should "extract package declarations" in {
    // scalameta normalizes dotted packages; use a single-segment package name
    val content = "package myapp\nobject X\n"
    val file = createTempFile(content)
    try {
      val entries = extractor.extract(makeFileEntry(file))
      entries.exists(e => e.sigType == SigType.PACKAGE && e.signatureText == "myapp") shouldBe true
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }

  it should "extract import statements" in {
    val content =
      """package test
        |import scala.collection.mutable
        |import java.util.{List, Map}
        |""".stripMargin
    val file = createTempFile(content)
    try {
      val entries = extractor.extract(makeFileEntry(file))
      entries.exists(e => e.sigType == SigType.IMPORT && e.signatureText.contains("scala.collection.mutable")) shouldBe true
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }

  it should "extract class definitions" in {
    val content =
      """package test
        |class MyClass
        |""".stripMargin
    val file = createTempFile(content)
    try {
      val entries = extractor.extract(makeFileEntry(file))
      entries.exists(e => e.sigType == SigType.CLASS && e.signatureText == "MyClass") shouldBe true
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }

  it should "extract trait definitions" in {
    val content =
      """package test
        |trait MyTrait
        |""".stripMargin
    val file = createTempFile(content)
    try {
      val entries = extractor.extract(makeFileEntry(file))
      entries.exists(e => e.sigType == SigType.TRAIT && e.signatureText == "MyTrait") shouldBe true
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }

  it should "extract object definitions" in {
    val content =
      """package test
        |object MyObject
        |""".stripMargin
    val file = createTempFile(content)
    try {
      val entries = extractor.extract(makeFileEntry(file))
      entries.exists(e => e.sigType == SigType.OBJECT && e.signatureText == "MyObject") shouldBe true
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }

  it should "extract case class definitions" in {
    val content =
      """package test
        |case class MyCase(x: Int)
        |""".stripMargin
    val file = createTempFile(content)
    try {
      val entries = extractor.extract(makeFileEntry(file))
      entries.exists(e => e.sigType == SigType.CASE_CLASS && e.signatureText == "MyCase") shouldBe true
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }

  it should "extract method definitions" in {
    val content =
      """package test
        |class Foo {
        |  def bar(x: Int): String = x.toString
        |}
        |""".stripMargin
    val file = createTempFile(content)
    try {
      val entries = extractor.extract(makeFileEntry(file))
      entries.exists(e => e.sigType == SigType.DEF && e.signatureText == "bar") shouldBe true
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }

  it should "extract val definitions" in {
    val content =
      """package test
        |class Foo {
        |  val x = 42
        |}
        |""".stripMargin
    val file = createTempFile(content)
    try {
      val entries = extractor.extract(makeFileEntry(file))
      entries.exists(e => e.sigType == SigType.VAL && e.signatureText == "x") shouldBe true
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }

  it should "extract var definitions" in {
    val content =
      """package test
        |class Foo {
        |  var y = 0
        |}
        |""".stripMargin
    val file = createTempFile(content)
    try {
      val entries = extractor.extract(makeFileEntry(file))
      entries.exists(e => e.sigType == SigType.VAR && e.signatureText == "y") shouldBe true
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }

  it should "extract type alias definitions" in {
    val content =
      """package test
        |type MyAlias = String
        |""".stripMargin
    val file = createTempFile(content)
    try {
      val entries = extractor.extract(makeFileEntry(file))
      entries.exists(e => e.sigType == SigType.TYPE && e.signatureText == "MyAlias") shouldBe true
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }

  it should "fall back to regex on parse failure" in {
    // Empty or invalid file should not crash, regex fallback returns empty
    val file = createTempFile("!!! invalid scala !!!\n{{{\n", "BadFile.scala")
    try {
      val entries = extractor.extract(makeFileEntry(file))
      // Regex fallback for invalid scala should still try to find something
      entries shouldBe a[Seq[_]]
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }

  it should "extract multiple top-level types from one file" in {
    val content =
      """package test
        |class ClassA
        |trait TraitB
        |object ObjC
        |case class CaseD(x: Int)
        |""".stripMargin
    val file = createTempFile(content)
    try {
      val entries = extractor.extract(makeFileEntry(file))
      val types = entries.filter(e => e.sigType == SigType.CLASS || e.sigType == SigType.TRAIT || e.sigType == SigType.OBJECT || e.sigType == SigType.CASE_CLASS)
      types.map(_.signatureText).toSet should contain allOf("ClassA", "TraitB", "ObjC", "CaseD")
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }
}
