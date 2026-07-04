package dtrbuilder.infrastructure

import dtrbuilder.application.LanguageDetector
import dtrbuilder.domain._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.nio.file.{Files}

/** Unit tests for RegexSignatureExtractor with fixture sources. */
class RegexSignatureExtractorSpec extends AnyFlatSpec with Matchers {

  private val extractor = new RegexSignatureExtractor

  behavior of "RegexSignatureExtractor"

  it should "extract Scala class definitions" in {
    val content = """package com.example
                    |import java.util.List
                    |class MyClass
                    |object MyObject
                    |trait MyTrait
                    |case class MyCase(x: Int)
                    |def myMethod = ???
                    |""".stripMargin

    val lang = LanguageDetector.defaultLanguages.find(_.name == "Scala").get
    val entries = extractor.extractFromContent("Test.scala", lang, content)

    entries.map(_.sigType) should contain(SigType.PACKAGE)
    entries.map(_.sigType) should contain(SigType.IMPORT)
    entries.map(_.sigType) should contain(SigType.CLASS)
    entries.map(_.sigType) should contain(SigType.OBJECT)
    entries.map(_.sigType) should contain(SigType.TRAIT)
    entries.map(_.sigType) should contain(SigType.CASE_CLASS)
    entries.map(_.sigType) should contain(SigType.DEF)
  }

  it should "extract Python class and function definitions" in {
    val content = """import os
                    |from pathlib import Path
                    |class MyClass:
                    |    def my_method(self):
                    |        pass
                    |""".stripMargin

    val lang = LanguageDetector.defaultLanguages.find(_.name == "Python").get
    val entries = extractor.extractFromContent("test.py", lang, content)

    entries.map(_.sigType) should contain(SigType.IMPORT)
    entries.map(_.sigType) should contain(SigType.FROM_IMPORT)
    entries.map(_.sigType) should contain(SigType.CLASS)
    entries.map(_.sigType) should contain(SigType.DEF)
  }

  it should "extract JavaScript exports and functions" in {
    val content = """import { something } from './lib'
                    |export class MyClass {}
                    |function myFunc() {}
                    |const value = 42
                    |""".stripMargin

    val lang = LanguageDetector.defaultLanguages.find(_.name == "JavaScript").get
    val entries = extractor.extractFromContent("test.js", lang, content)

    entries.map(_.sigType) should contain(SigType.IMPORT)
    entries.exists(e => e.sigType == SigType.EXPORT && e.signatureText.contains("MyClass")) shouldBe true
    entries.exists(e => e.sigType == SigType.FUNCTION && e.signatureText.contains("myFunc")) shouldBe true
  }

  it should "return empty for unknown language" in {
    val lang = Language("Unknown", Seq.empty, Seq.empty, Seq.empty)
    val entries = extractor.extractFromContent("test.xyz", lang, "some content")
    entries shouldBe empty
  }

  it should "extract from actual file on disk" in {
    val tempDir = Files.createTempDirectory("dtr-test-")
    try {
      val file = tempDir.resolve("TestFile.scala")
      Files.write(file, "class ExtractedFromFile".getBytes)

      val entry = FileEntry(
        relPath = "TestFile.scala",
        absolutePath = file,
        byteSize = Files.size(file),
        mimeType = "text/x-scala",
        encoding = "UTF-8",
        language = Some(LanguageDetector.defaultLanguages.find(_.name == "Scala").get),
        extension = "scala",
        isDirectory = false
      )

      val entries = extractor.extract(entry)
      entries.exists(e => e.sigType == SigType.CLASS && e.signatureText == "ExtractedFromFile") shouldBe true
    } finally {
      Files.deleteIfExists(tempDir.resolve("TestFile.scala"))
      Files.deleteIfExists(tempDir)
    }
  }
}
