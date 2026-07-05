package dtrbuilder.baseline

import dtrbuilder.infrastructure.ClasspathSeedTemplateLoader
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Unit tests for the seed DTR template and its loader.
  *
  * Verifies:
  * - seed-dtr.dtr exists on the classpath
  * - Contains expected keys (ARCH, LAYER, META, FILE, CODEX, TYPE, REL)
  * - Contains placeholder tokens ({{APP_NAME}}, {{ROOT_PATH}}, etc.)
  * - No package-specific tokens remain
  * - Can be loaded via ClasspathSeedTemplateLoader
  */
class SeedTemplateSpec extends AnyFlatSpec with Matchers {

  private val loader = new ClasspathSeedTemplateLoader

  behavior of "ClasspathSeedTemplateLoader"

  it should "load the seed template from classpath" in {
    val content = loader.loadSeed()
    content should not be empty
  }

  it should "contain ARCH line" in {
    val content = loader.loadSeed()
    content should include("ARCH=")
  }

  it should "contain LAYER.ORDER line" in {
    val content = loader.loadSeed()
    content should include("LAYER.ORDER=")
  }

  it should "contain META keys" in {
    val content = loader.loadSeed()
    content should include("META.GENERATOR=")
    content should include("META.APP_NAME=")
    content should include("META.ROOT_PATH=")
  }

  it should "not contain PACKAGE_NAME" in {
    val content = loader.loadSeed()
    content should not include "PACKAGE_NAME"
  }

  it should "contain FILE entries" in {
    val content = loader.loadSeed()
    content should include("FILE.")
  }

  it should "contain CODEX entries" in {
    val content = loader.loadSeed()
    content should include("CODEX.")
  }

  it should "contain TYPE entries" in {
    val content = loader.loadSeed()
    content should include("TYPE.")
  }

  it should "contain REL entries" in {
    val content = loader.loadSeed()
    content should include("REL.")
  }

  it should "contain placeholder tokens for substitution" in {
    val content = loader.loadSeed()
    content should include("{{APP_NAME}}")
    content should include("{{ROOT_PATH}}")
    content should include("{{LANGUAGE}}")
    content should include("{{TIMESTAMP}}")
  }

  it should "not contain PACKAGE_PATH or PACKAGE_NAME tokens" in {
    val content = loader.loadSeed()
    content should not include "{{PACKAGE_NAME}}"
    content should not include "{{PACKAGE_PATH}}"
  }

  it should "contain domain layer files" in {
    val content = loader.loadSeed()
    content should include("domain/Model.scala")
    content should include("domain/ValueObject.scala")
  }

  it should "contain application layer files" in {
    val content = loader.loadSeed()
    content should include("application/Service.scala")
    content should include("application/Port.scala")
  }

  it should "contain infrastructure layer files" in {
    val content = loader.loadSeed()
    content should include("infrastructure/Adapter.scala")
    content should include("infrastructure/EnvironmentImpl.scala")
  }

  it should "contain control layer files" in {
    val content = loader.loadSeed()
    content should include("control/Container.scala")
    content should include("control/CliParser.scala")
    content should include("control/App.scala")
  }

  it should "load successfully multiple times" in {
    val content1 = loader.loadSeed()
    val content2 = loader.loadSeed()
    content1 shouldBe content2
  }
}
