package dtrbuilder.infrastructure

import dtrbuilder.application.LanguageDetector
import dtrbuilder.domain._
import dtrbuilder.infrastructure.ast.JavaScriptAstExtractor
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.nio.file.{Files, Path}

/** Unit tests for JavaScriptAstExtractor with temporary JavaScript source files. */
class JavaScriptAstExtractorSpec extends AnyFlatSpec with Matchers {

  private val regexExtractor = new RegexSignatureExtractor
  private val extractor = new JavaScriptAstExtractor(regexExtractor)

  private def createTempFile(content: String, fileName: String = "TestFile.js"): Path = {
    val tempDir = Files.createTempDirectory("js-ast-test-")
    val file = tempDir.resolve(fileName)
    Files.write(file, content.getBytes("UTF-8"))
    file
  }

  private def makeFileEntry(file: Path, langName: String = "JavaScript"): FileEntry = {
    val lang = LanguageDetector.defaultLanguages.find(_.name == langName)
    FileEntry(
      relPath = file.getFileName.toString,
      absolutePath = file,
      byteSize = Files.size(file),
      mimeType = "text/javascript",
      encoding = "UTF-8",
      language = lang,
      extension = "js",
      isDirectory = false
    )
  }

  behavior of "JavaScriptAstExtractor"

  it should "extract import statements" in {
    val content = """import { foo } from './bar';
                    |import baz from 'qux';
                    |""".stripMargin
    val file = createTempFile(content)
    try {
      val entries = extractor.extract(makeFileEntry(file))
      val imports = entries.filter(_.sigType == SigType.IMPORT)
      imports should not be empty
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }

  it should "extract export declarations" in {
    val content = "export const x = 42;\n"
    val file = createTempFile(content)
    try {
      val entries = extractor.extract(makeFileEntry(file))
      entries.exists(e => e.sigType == SigType.EXPORT) shouldBe true
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }

  it should "extract function declarations" in {
    val content =
      """function greet(name) {
        |    return "Hello " + name;
        |}
        |""".stripMargin
    val file = createTempFile(content)
    try {
      val entries = extractor.extract(makeFileEntry(file))
      entries.exists(e => e.sigType == SigType.FUNCTION && e.signatureText == "function greet") shouldBe true
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }

  it should "extract class declarations" in {
    val content =
      """class Animal {
        |    constructor(name) { this.name = name; }
        |}
        |""".stripMargin
    val file = createTempFile(content)
    try {
      val entries = extractor.extract(makeFileEntry(file))
      entries.exists(e => e.sigType == SigType.CLASS && e.signatureText == "class Animal") shouldBe true
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }

  it should "extract const/let/var declarations" in {
    val content =
      """const PI = 3.14;
        |let count = 0;
        |var name = 'test';
        |""".stripMargin
    val file = createTempFile(content)
    try {
      val entries = extractor.extract(makeFileEntry(file))
      entries.exists(e => e.sigType == SigType.CONST && e.signatureText.contains("PI")) shouldBe true
      entries.exists(e => e.sigType == SigType.CONST && e.signatureText.contains("count")) shouldBe true
      entries.exists(e => e.sigType == SigType.CONST && e.signatureText.contains("name")) shouldBe true
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }

  it should "fall back to regex on parse failure" in {
    val file = createTempFile("!!! invalid js !!!\n{{{", "BadFile.js")
    try {
      val entries = extractor.extract(makeFileEntry(file))
      entries shouldBe a[Seq[_]]
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }
}
