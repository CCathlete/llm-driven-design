package dtrbuilder.infrastructure

import dtrbuilder.domain.models.Language
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Unit tests for LanguageProfileLoader loading from classpath resources. */
class LanguageProfileLoaderSpec extends AnyFlatSpec with Matchers {

  private val loader = new dtrbuilder.infrastructure.detection.ClasspathLanguageProfileLoader

  behavior of "ClasspathLanguageProfileLoader"

  it should "load the Scala profile from classpath" in {
    val lang = loader.load("languages/scala.properties")
    lang should be(defined)
    lang.get.name shouldBe "Scala"
    lang.get.extensions should contain("scala")
  }

  it should "load the Java profile from classpath" in {
    val lang = loader.load("languages/java.properties")
    lang should be(defined)
    lang.get.name shouldBe "Java"
    lang.get.extensions should contain("java")
  }

  it should "load the Python profile from classpath" in {
    val lang = loader.load("languages/python.properties")
    lang should be(defined)
    lang.get.name shouldBe "Python"
    lang.get.extensions should contain("py")
  }

  it should "load all language profiles via loadAll" in {
    val all = loader.loadAll()
    all.map(_.name).toSet should contain allOf("Scala", "Java", "Python", "JavaScript", "TypeScript")
    all.size should be >= 5
  }

  it should "return None for non-existent resource" in {
    val lang = loader.load("languages/nonexistent.properties")
    lang shouldBe None
  }

  it should "return profiles with regex patterns defined" in {
    val scala = loader.load("languages/scala.properties")
    scala.get.regexPatterns should not be empty
  }

  it should "load profiles with shebangs where applicable" in {
    val python = loader.load("languages/python.properties")
    python.get.shebangs should not be empty
  }

  it should "load JavaScript profile" in {
    val lang = loader.load("languages/javascript.properties")
    lang should be(defined)
    lang.get.name shouldBe "JavaScript"
    lang.get.extensions should contain("js")
  }

  it should "load TypeScript profile" in {
    val lang = loader.load("languages/typescript.properties")
    lang should be(defined)
    lang.get.name shouldBe "TypeScript"
    lang.get.extensions should contain("ts")
  }

  it should "load Markdown profile" in {
    val lang = loader.load("languages/markdown.properties")
    lang should be(defined)
    lang.get.name shouldBe "Markdown"
    lang.get.extensions should contain("md")
  }

  it should "load YAML profile" in {
    val lang = loader.load("languages/yaml.properties")
    lang should be(defined)
    lang.get.extensions should contain atLeastOneOf("yaml", "yml")
  }

  it should "load JSON profile" in {
    val lang = loader.load("languages/json.properties")
    lang should be(defined)
    lang.get.name shouldBe "JSON"
    lang.get.extensions should contain("json")
  }

  it should "load C profile" in {
    val lang = loader.load("languages/c.properties")
    lang should be(defined)
    lang.get.name shouldBe "C"
    lang.get.extensions should contain("c")
  }

  it should "load C++ profile" in {
    val lang = loader.load("languages/cpp.properties")
    lang should be(defined)
    lang.get.name shouldBe "C++"
    lang.get.extensions should contain("cpp")
  }

  it should "load Go profile" in {
    val lang = loader.load("languages/go.properties")
    lang should be(defined)
    lang.get.name shouldBe "Go"
    lang.get.extensions should contain("go")
  }

  it should "load Rust profile" in {
    val lang = loader.load("languages/rust.properties")
    lang should be(defined)
    lang.get.name shouldBe "Rust"
    lang.get.extensions should contain("rs")
  }

  it should "load Ruby profile" in {
    val lang = loader.load("languages/ruby.properties")
    lang should be(defined)
    lang.get.name shouldBe "Ruby"
    lang.get.extensions should contain("rb")
  }

  it should "load Shell profile" in {
    val lang = loader.load("languages/shell.properties")
    lang should be(defined)
    lang.get.name shouldBe "Shell"
    lang.get.extensions should contain("sh")
  }
}
