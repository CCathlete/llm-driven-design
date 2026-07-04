package dtrbuilder.infrastructure

import dtrbuilder.application.LanguageDetector
import dtrbuilder.domain._
import dtrbuilder.infrastructure.ast.PythonAstExtractor
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.nio.file.{Files, Path}

/** Unit tests for PythonAstExtractor with temporary Python source files. */
class PythonAstExtractorSpec extends AnyFlatSpec with Matchers {

  private val regexExtractor = new RegexSignatureExtractor
  private val extractor = new PythonAstExtractor(regexExtractor)

  private def createTempFile(content: String, fileName: String = "TestFile.py"): Path = {
    val tempDir = Files.createTempDirectory("python-ast-test-")
    val file = tempDir.resolve(fileName)
    Files.write(file, content.getBytes("UTF-8"))
    file
  }

  private def makeFileEntry(file: Path, langName: String = "Python"): FileEntry = {
    val lang = LanguageDetector.defaultLanguages.find(_.name == langName)
    FileEntry(
      relPath = file.getFileName.toString,
      absolutePath = file,
      byteSize = Files.size(file),
      mimeType = "text/x-python",
      encoding = "UTF-8",
      language = lang,
      extension = "py",
      isDirectory = false
    )
  }

  behavior of "PythonAstExtractor"

  it should "extract simple import statements" in {
    val content = "import os\nimport sys\n"
    val file = createTempFile(content)
    try {
      val entries = extractor.extract(makeFileEntry(file))
      val imports = entries.filter(_.sigType == SigType.IMPORT)
      imports.map(_.signatureText) should contain allOf("import os", "import sys")
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }

  it should "extract from-import statements" in {
    val content = "from collections import defaultdict, OrderedDict\n"
    val file = createTempFile(content)
    try {
      val entries = extractor.extract(makeFileEntry(file))
      entries.exists(e => e.sigType == SigType.FROM_IMPORT) shouldBe true
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }

  it should "extract class definitions" in {
    val content =
      """class MyClass:
        |    pass
        |""".stripMargin
    val file = createTempFile(content)
    try {
      val entries = extractor.extract(makeFileEntry(file))
      entries.exists(e => e.sigType == SigType.CLASS && e.signatureText == "class MyClass") shouldBe true
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }

  it should "extract function definitions" in {
    val content =
      """def hello():
        |    pass
        |""".stripMargin
    val file = createTempFile(content)
    try {
      val entries = extractor.extract(makeFileEntry(file))
      entries.exists(e => e.sigType == SigType.FUNCTION && e.signatureText == "def hello") shouldBe true
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }

  it should "extract async function definitions" in {
    val content =
      """async def fetch_data():
        |    pass
        |""".stripMargin
    val file = createTempFile(content)
    try {
      val entries = extractor.extract(makeFileEntry(file))
      entries.exists(e => e.sigType == SigType.FUNCTION && e.signatureText == "async def fetch_data") shouldBe true
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }

  it should "extract decorated functions" in {
    val content =
      """@staticmethod
        |@decorator
        |def my_method():
        |    pass
        |""".stripMargin
    val file = createTempFile(content)
    try {
      val entries = extractor.extract(makeFileEntry(file))
      entries.exists(e => e.sigType == SigType.FUNCTION && e.signatureText.contains("my_method")) shouldBe true
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }

  it should "fall back to regex on parse failure" in {
    val file = createTempFile("!!! invalid python !!!\n{{{\n", "BadFile.py")
    try {
      val entries = extractor.extract(makeFileEntry(file))
      entries shouldBe a[Seq[_]]
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }
}
