package hypothesisengine

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import java.nio.file.{Files, Paths}

class RuntimeSpec extends AnyFunSpec with Matchers:
  describe("standalone runtime and fresh start"):
    it("carries the embedded ledger schema in the artifact"):
      val resource = getClass.getResourceAsStream("/ledger-schema.sql")
      resource should not be null
      val schema: String = scala.io.Source.fromInputStream(resource).mkString
      schema.contains("CREATE TABLE IF NOT EXISTS executions") shouldBe true

    it("has no external database server configuration"):
      val build: String = scala.io.Source.fromFile("build.sbt").mkString
      build.contains("sqlite-jdbc") shouldBe true
      build.contains("jdbc:postgresql") shouldBe false

    it("defines the cleanLedger task and the fresh flag"):
      val build: String = scala.io.Source.fromFile("build.sbt").mkString
      build.contains("cleanLedger") shouldBe true
      build.contains("hypothesis-engine.ledger.fresh") shouldBe true

    it("requires no python to start the engine"):
      val main: String = scala.io.Source.fromFile("src/main/scala/hypothesisengine/control/Main.scala").mkString
      main.contains("object Main") shouldBe true
      main.contains("ProcessBuilder") shouldBe false