package dtrbuilder.domain.models

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.nio.file.Paths

/** Unit tests for domain models: construction, equality, serialization. */
class DomainModelSpec extends AnyFlatSpec with Matchers {

  "DtrConfig" should "construct with defaults" in {
    val config = DtrConfig(Paths.get("/root"), Paths.get("out.itr"))
    config.pathRoot shouldBe Paths.get("/root")
    config.outputPath shouldBe Paths.get("out.itr")
    config.maxChunkSize shouldBe DtrConfig.defaultMaxChunkSize
    config.chunkEnabled shouldBe true
    config.noDotenv shouldBe false
  }

  it should "accept optional overrides" in {
    val config = DtrConfig(
      pathRoot = Paths.get("/project"),
      outputPath = Paths.get("/out/dtr.itr"),
      maxChunkSize = 2048L,
      chunkEnabled = true,
      noDotenv = true,
      additionalFilters = Seq("*.log")
    )
    config.maxChunkSize shouldBe 2048L
    config.noDotenv shouldBe true
    config.additionalFilters should contain("*.log")
  }

  "FileEntry" should "construct correctly" in {
    val entry = FileEntry(
      relPath = "src/main.scala",
      absolutePath = Paths.get("/root/src/main.scala"),
      byteSize = 1024L,
      mimeType = "text/x-scala",
      encoding = "UTF-8",
      language = None,
      extension = "scala",
      isDirectory = false
    )
    entry.relPath shouldBe "src/main.scala"
    entry.extension shouldBe "scala"
    entry.isDirectory shouldBe false
  }

  "CodexEntry" should "hold relPath, sigType, and signatureText" in {
    val entry = CodexEntry("Main.scala", SigType.CLASS, "MyClass")
    entry.relPath shouldBe "Main.scala"
    entry.sigType shouldBe SigType.CLASS
    entry.signatureText shouldBe "MyClass"
  }

  "TypeDef" should "hold fqn, kind, and source" in {
    val td = TypeDef("com.example.MyClass", "CLASS", "src/MyClass.scala")
    td.fullyQualifiedName shouldBe "com.example.MyClass"
    td.kind shouldBe "CLASS"
    td.sourceFile shouldBe "src/MyClass.scala"
  }

  "Relation" should "hold from, to, type, detail" in {
    val rel = Relation("A", "B", RelationType.EXTENDS, "A extends B")
    rel.fromFqn shouldBe "A"
    rel.toFqn shouldBe "B"
    rel.relationType shouldBe RelationType.EXTENDS
    rel.detail shouldBe "A extends B"
  }

  "RelationType" should "parse from string" in {
    RelationType.fromString("EXTENDS") should contain(RelationType.EXTENDS)
    RelationType.fromString("import_dep") should contain(RelationType.IMPORT_DEP)
    RelationType.fromString("UNKNOWN") shouldBe None
  }

  "SigType" should "parse from string" in {
    SigType.fromString("CLASS") should contain(SigType.CLASS)
    SigType.fromString("trait") should contain(SigType.TRAIT)
    SigType.fromString("unknown") should contain(SigType.UNKNOWN)
  }

  "Signature" should "support Text and Typed variants" in {
    val text = Signature.Text("myMethod")
    val typed = Signature.Typed(SigType.DEF, "myMethod")

    text shouldBe a[Signature.Text]
    typed shouldBe a[Signature.Typed]
  }

  "RawEntry" should "hold a tensor line" in {
    val entry = RawEntry("KEY=VALUE")
    entry.tensorLine shouldBe "KEY=VALUE"
  }

  "DtrChunk" should "hold entries, size, index" in {
    val chunk = DtrChunk(
      entries = Seq(RawEntry("A=1"), RawEntry("B=2")),
      byteSize = 42L,
      chunkIndex = 0,
      totalChunks = 2
    )
    chunk.entries should have size 2
    chunk.chunkIndex shouldBe 0
    chunk.totalChunks shouldBe 2
  }

  "DtrDocument" should "compute counts" in {
    val doc = DtrDocument(
      root = Paths.get("/root"),
      chunks = Seq.empty,
      fileEntries = Seq(
        FileEntry("a.scala", Paths.get("/root/a.scala"), 10, "text/plain", "UTF-8", None, "scala", false),
        FileEntry("b.scala", Paths.get("/root/b.scala"), 20, "text/plain", "UTF-8", None, "scala", false)
      ),
      codexEntries = Seq(CodexEntry("a.scala", SigType.CLASS, "A")),
      typeEntries = Seq(TypeDef("A", "CLASS", "a.scala")),
      relationEntries = Seq.empty,
      meta = Map("key" -> "value")
    )
    doc.fileCount shouldBe 2
    doc.codexCount shouldBe 1
    doc.typeCount shouldBe 1
    doc.relationCount shouldBe 0
  }

  "DotEnv" should "merge with system env" in {
    val dotEnv = DotEnv(Map("FOO" -> "bar", "BAZ" -> "qux"), None)
    val merged = dotEnv.mergedWithSystem(Map("FOO" -> "system"))
    merged.get("FOO") should contain("system") // system takes precedence
    merged.get("BAZ") should contain("qux")
  }
}
