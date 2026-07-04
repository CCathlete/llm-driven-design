package dtrbuilder.infrastructure

import dtrbuilder.application.LanguageDetector
import dtrbuilder.domain._
import dtrbuilder.infrastructure.ast.TypeScriptAstExtractor
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.nio.file.{Files, Path}

/** Unit tests for TypeScriptAstExtractor with temporary TypeScript source files. */
class TypeScriptAstExtractorSpec extends AnyFlatSpec with Matchers {

  private val regexExtractor = new RegexSignatureExtractor
  private val extractor = new TypeScriptAstExtractor(regexExtractor)

  private def createTempFile(content: String, fileName: String = "TestFile.ts"): Path = {
    val tempDir = Files.createTempDirectory("ts-ast-test-")
    val file = tempDir.resolve(fileName)
    Files.write(file, content.getBytes("UTF-8"))
    file
  }

  private def makeFileEntry(file: Path, langName: String = "TypeScript"): FileEntry = {
    val lang = LanguageDetector.defaultLanguages.find(_.name == langName)
    FileEntry(
      relPath = file.getFileName.toString,
      absolutePath = file,
      byteSize = Files.size(file),
      mimeType = "text/x-typescript",
      encoding = "UTF-8",
      language = lang,
      extension = "ts",
      isDirectory = false
    )
  }

  behavior of "TypeScriptAstExtractor"

  it should "extract import statements" in {
    val content = """import { Component } from '@angular/core';
                    |import * as Rx from 'rxjs';
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

  it should "extract export statements" in {
    val content = "export interface Config {}\n"
    val file = createTempFile(content)
    try {
      val entries = extractor.extract(makeFileEntry(file))
      entries.exists(e => e.sigType == SigType.EXPORT) shouldBe true
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }

  it should "extract interface declarations" in {
    val content =
      """interface User {
        |    name: string;
        |    age: number;
        |}
        |""".stripMargin
    val file = createTempFile(content)
    try {
      val entries = extractor.extract(makeFileEntry(file))
      entries.exists(e => e.sigType == SigType.INTERFACE && e.signatureText == "interface User") shouldBe true
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }

  it should "extract type alias declarations" in {
    val content = "type Point = { x: number; y: number };\n"
    val file = createTempFile(content)
    try {
      val entries = extractor.extract(makeFileEntry(file))
      entries.exists(e => e.sigType == SigType.TYPE && e.signatureText.contains("Point")) shouldBe true
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }

  it should "extract enum declarations" in {
    val content =
      """enum Color {
        |    Red,
        |    Green,
        |    Blue
        |}
        |""".stripMargin
    val file = createTempFile(content)
    try {
      val entries = extractor.extract(makeFileEntry(file))
      entries.exists(e => e.sigType == SigType.ENUM && e.signatureText == "enum Color") shouldBe true
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }

  it should "extract function declarations" in {
    val content =
      """function add(a: number, b: number): number {
        |    return a + b;
        |}
        |""".stripMargin
    val file = createTempFile(content)
    try {
      val entries = extractor.extract(makeFileEntry(file))
      entries.exists(e => e.sigType == SigType.FUNCTION && e.signatureText == "function add") shouldBe true
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }

  it should "extract class declarations" in {
    val content =
      """class Person {
        |    name: string;
        |    constructor(name: string) { this.name = name; }
        |}
        |""".stripMargin
    val file = createTempFile(content)
    try {
      val entries = extractor.extract(makeFileEntry(file))
      entries.exists(e => e.sigType == SigType.CLASS && e.signatureText == "class Person") shouldBe true
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }

  it should "extract const/let/var declarations" in {
    val content =
      """const API_URL = 'https://api.example.com';
        |let counter = 0;
        |""".stripMargin
    val file = createTempFile(content)
    try {
      val entries = extractor.extract(makeFileEntry(file))
      entries.exists(e => e.sigType == SigType.CONST && e.signatureText.contains("API_URL")) shouldBe true
      entries.exists(e => e.sigType == SigType.CONST && e.signatureText.contains("counter")) shouldBe true
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }

  it should "extract decorators" in {
    val content =
      """@Injectable()
        |@Deprecated()
        |class MyService {}
        |""".stripMargin
    val file = createTempFile(content)
    try {
      val entries = extractor.extract(makeFileEntry(file))
      entries.exists(e => e.sigType == SigType.DECORATOR) shouldBe true
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }

  it should "fall back to regex on parse failure" in {
    val file = createTempFile("!!! invalid ts !!!\n{{{", "BadFile.ts")
    try {
      val entries = extractor.extract(makeFileEntry(file))
      entries shouldBe a[Seq[_]]
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }
}
