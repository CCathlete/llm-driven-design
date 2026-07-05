package dtrbuilder.baseline

import dtrbuilder.application.{BaselineDtrGenerator, SeedTemplateLoader}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Unit tests for BaselineDtrGenerator.
  *
  * Verifies:
  * - Token substitution works correctly
  * - Multiple app names produce correct output
  * - Output contains valid DTR lines
  * - Comments are stripped
  */
class BaselineDtrGeneratorSpec extends AnyFlatSpec with Matchers {

  // A minimal seed template for testing (no package tokens)
  private val testSeed: String =
    """# Seed DTR — Baseline design tensor for {{APP_NAME}}
      |ARCH=HEX,DI,DIP
      |LAYER.ORDER=DOMAIN,APPLICATION,INFRASTRUCTURE,CONTROL
      |META.APP_NAME={{APP_NAME}}
      |META.ROOT_PATH={{ROOT_PATH}}
      |META.TIMESTAMP={{TIMESTAMP}}
      |META.LANGUAGE={{LANGUAGE}}
      |FILE.{{ROOT_PATH}}/src/main/scala/domain/Model.scala=SIZE:0,MIME:text/x-scala,ENCODING:UTF-8,LANG:Scala,EXT:scala
      |CODEX.{{ROOT_PATH}}/src/main/scala/domain/Model.scala=CLASS:Model
      |TYPE.{{APP_NAME}}.domain.Model=KIND:CLASS,FILE:{{ROOT_PATH}}/src/main/scala/domain/Model.scala
      |REL.{{APP_NAME}}.infrastructure.Adapter->{{APP_NAME}}.application.Port=IMPLEMENTS:Adapter implements Port
      |""".stripMargin.trim

  /** A test SeedTemplateLoader that returns the minimal seed. */
  private class TestSeedTemplateLoader extends SeedTemplateLoader {
    override def loadSeed(): String = testSeed
  }

  private def makeGenerator(): BaselineDtrGenerator = {
    val loader = new TestSeedTemplateLoader
    new BaselineDtrGenerator(loader)
  }

  behavior of "BaselineDtrGenerator"

  it should "substitute {{APP_NAME}} correctly" in {
    val generator = makeGenerator()
    val entries = generator.generate(
      appName  = "my-app",
      rootPath = "/home/user/my-app",
      language = "Scala"
    )
    val rendered = entries.map(_.tensorLine).mkString("\n")
    rendered should include("my-app")
    rendered should not include "{{APP_NAME}}"
  }

  it should "substitute {{ROOT_PATH}} correctly" in {
    val generator = makeGenerator()
    val entries = generator.generate(
      appName  = "app",
      rootPath = "/projects/my-app",
      language = "Scala"
    )
    val rendered = entries.map(_.tensorLine).mkString("\n")
    rendered should include("/projects/my-app")
    rendered should not include "{{ROOT_PATH}}"
  }

  it should "substitute {{LANGUAGE}} correctly" in {
    val generator = makeGenerator()
    val entries = generator.generate(
      appName  = "app",
      rootPath = "/root",
      language = "Python"
    )
    val rendered = entries.map(_.tensorLine).mkString("\n")
    rendered should include("Python")
    rendered should not include "{{LANGUAGE}}"
  }

  it should "substitute {{TIMESTAMP}} with a valid ISO instant" in {
    val generator = makeGenerator()
    val entries = generator.generate(
      appName  = "app",
      rootPath = "/root",
      language = "Scala"
    )
    val rendered = entries.map(_.tensorLine).mkString("\n")
    // TIMESTAMP is replaced with an ISO-8601 instant (contains T and Z or +)
    val tsLine = rendered.linesIterator.find(_.startsWith("META.TIMESTAMP="))
    tsLine shouldBe defined
    tsLine.get should include("T") // ISO instant contains 'T'
  }

  it should "strip comment lines from output" in {
    val generator = makeGenerator()
    val entries = generator.generate(
      appName  = "app",
      rootPath = "/root",
      language = "Scala"
    )
    val rendered = entries.map(_.tensorLine).mkString("\n")
    rendered should not include "#"
    rendered should not include "Seed DTR"
  }

  it should "produce valid DTR lines (KEY=VALUE format)" in {
    val generator = makeGenerator()
    val entries = generator.generate(
      appName  = "app",
      rootPath = "/root",
      language = "Scala"
    )
    entries.foreach { entry =>
      entry.tensorLine should include("=")
    }
  }

  it should "produce ARCH, LAYER, META, FILE, CODEX, TYPE, REL entries" in {
    val generator = makeGenerator()
    val entries = generator.generate(
      appName  = "app",
      rootPath = "/root",
      language = "Scala"
    )
    val rendered = entries.map(_.tensorLine).mkString("\n")
    rendered should include("ARCH=")
    rendered should include("LAYER.ORDER=")
    rendered should include("META.")
    rendered should include("FILE.")
    rendered should include("CODEX.")
    rendered should include("TYPE.")
    rendered should include("REL.")
  }

  it should "handle various app names without issues" in {
    val generator = makeGenerator()

    val names = Seq("simple", "my-app", "my_app", "App123", "a.b.c")
    names.foreach { name =>
      val entries = generator.generate(
        appName  = name,
        rootPath = "/root",
        language = "Scala"
      )
      entries should not be empty
      entries.foreach(_.tensorLine should include("="))
    }
  }

  it should "handle various root paths without issues" in {
    val generator = makeGenerator()

    val roots = Seq("/home/user", "/", "/deeply/nested/path", "/with spaces")
    roots.foreach { root =>
      val entries = generator.generate(
        appName  = "app",
        rootPath = root,
        language = "Scala"
      )
      entries should not be empty
      entries.foreach(_.tensorLine should include("="))
    }
  }

  it should "produce lines without leading/trailing whitespace" in {
    val generator = makeGenerator()
    val entries = generator.generate(
      appName  = "app",
      rootPath = "/root",
      language = "Scala"
    )
    entries.foreach { entry =>
      entry.tensorLine shouldBe entry.tensorLine.trim
    }
  }
}
