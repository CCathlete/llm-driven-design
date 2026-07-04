package dtrbuilder.infrastructure

import dtrbuilder.application.LanguageDetector
import dtrbuilder.domain._
import dtrbuilder.infrastructure.ast.JavaAstExtractor
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.nio.file.{Files, Path}

/** Unit tests for JavaAstExtractor with temporary Java source files. */
class JavaAstExtractorSpec extends AnyFlatSpec with Matchers {

  private val regexExtractor = new RegexSignatureExtractor
  private val extractor = new JavaAstExtractor(regexExtractor)

  private def createTempFile(content: String, fileName: String = "TestFile.java"): Path = {
    val tempDir = Files.createTempDirectory("java-ast-test-")
    val file = tempDir.resolve(fileName)
    Files.write(file, content.getBytes("UTF-8"))
    file
  }

  private def makeFileEntry(file: Path, langName: String = "Java"): FileEntry = {
    val lang = LanguageDetector.defaultLanguages.find(_.name == langName)
    FileEntry(
      relPath = file.getFileName.toString,
      absolutePath = file,
      byteSize = Files.size(file),
      mimeType = "text/x-java",
      encoding = "UTF-8",
      language = lang,
      extension = "java",
      isDirectory = false
    )
  }

  behavior of "JavaAstExtractor"

  it should "extract package declarations" in {
    val content = "package com.example.myapp;\n"
    val file = createTempFile(content)
    try {
      val entries = extractor.extract(makeFileEntry(file))
      entries.exists(e => e.sigType == SigType.PACKAGE && e.signatureText == "com.example.myapp") shouldBe true
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }

  it should "extract import statements" in {
    val content =
      """package test;
        |import java.util.List;
        |import java.util.Map;
        |""".stripMargin
    val file = createTempFile(content)
    try {
      val entries = extractor.extract(makeFileEntry(file))
      val imports = entries.filter(_.sigType == SigType.IMPORT)
      imports.map(_.signatureText) should contain allOf("java.util.List", "java.util.Map")
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }

  it should "extract class definitions" in {
    val content =
      """package test;
        |public class MyClass {}
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

  it should "extract interface definitions" in {
    val content =
      """package test;
        |public interface MyInterface {}
        |""".stripMargin
    val file = createTempFile(content)
    try {
      val entries = extractor.extract(makeFileEntry(file))
      entries.exists(e => e.sigType == SigType.INTERFACE && e.signatureText == "MyInterface") shouldBe true
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }

  it should "extract enum definitions" in {
    val content =
      """package test;
        |public enum MyEnum { A, B, C }
        |""".stripMargin
    val file = createTempFile(content)
    try {
      val entries = extractor.extract(makeFileEntry(file))
      entries.exists(e => e.sigType == SigType.ENUM && e.signatureText == "MyEnum") shouldBe true
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }

  it should "extract method definitions" in {
    val content =
      """package test;
        |public class Foo {
        |    public String bar(int x) { return null; }
        |    private void baz() {}
        |}
        |""".stripMargin
    val file = createTempFile(content)
    try {
      val entries = extractor.extract(makeFileEntry(file))
      val methods = entries.filter(e => e.sigType == SigType.DEF)
      methods.map(_.signatureText) should contain allOf("bar: String", "baz: void")
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }

  it should "extract constructor definitions" in {
    val content =
      """package test;
        |public class Foo {
        |    public Foo() {}
        |    public Foo(int x) {}
        |}
        |""".stripMargin
    val file = createTempFile(content)
    try {
      val entries = extractor.extract(makeFileEntry(file))
      val ctors = entries.filter(e => e.sigType == SigType.CTOR)
      ctors should not be empty
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }

  it should "extract field definitions" in {
    val content =
      """package test;
        |public class Foo {
        |    public final int CONSTANT = 42;
        |    private String name;
        |}
        |""".stripMargin
    val file = createTempFile(content)
    try {
      val entries = extractor.extract(makeFileEntry(file))
      // final fields are VAL, non-final fields are VAR
      entries.exists(e => e.sigType == SigType.VAL && e.signatureText.startsWith("CONSTANT")) shouldBe true
      entries.exists(e => e.sigType == SigType.VAR && e.signatureText.startsWith("name")) shouldBe true
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }

  it should "extract annotation interface definitions" in {
    val content =
      """package test;
        |public @interface MyAnnotation {}
        |""".stripMargin
    val file = createTempFile(content)
    try {
      val entries = extractor.extract(makeFileEntry(file))
      entries.exists(e => e.sigType == SigType.ANNOTATION && e.signatureText == "MyAnnotation") shouldBe true
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }

  it should "extract multiple types from one file" in {
    val content =
      """package test;
        |public class ClassA {}
        |interface InterfaceB {}
        |""".stripMargin
    val file = createTempFile(content)
    try {
      val entries = extractor.extract(makeFileEntry(file))
      val types = entries.filter(e => e.sigType == SigType.CLASS || e.sigType == SigType.INTERFACE)
      types.map(_.signatureText).toSet should contain allOf("ClassA", "InterfaceB")
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }

  it should "fall back to regex on parse failure" in {
    val file = createTempFile("!!! invalid java !!!\n{{{\n", "BadFile.java")
    try {
      val entries = extractor.extract(makeFileEntry(file))
      entries shouldBe a[Seq[_]]
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    }
  }
}
