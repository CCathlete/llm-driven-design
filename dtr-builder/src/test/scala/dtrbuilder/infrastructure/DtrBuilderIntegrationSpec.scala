package dtrbuilder.infrastructure

import dtrbuilder.application.ports._
import dtrbuilder.application.services._
import dtrbuilder.control.dependency_injection.Container
import dtrbuilder.domain.models._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.nio.file.{Files, Path}

/** Integration test: end-to-end DTR building with a multi-language fixture directory and .env file. */
class DtrBuilderIntegrationSpec extends AnyFlatSpec with Matchers {

  behavior of "DtrBuilderService (integration)"

  it should "build a DTR from a multi-language fixture directory" in {
    // Create a temporary fixture directory with multiple language files
    val fixtureDir = Files.createTempDirectory("dtr-int-")
    val outputPath = fixtureDir.resolve("output.itr")

    try {
      // Create .env file
      Files.write(fixtureDir.resolve(".env"),
        """PROJECT_NAME=integration-test
          |DTR_MAX_CHUNK_SIZE=50000
          |""".stripMargin.getBytes)

      // Create Scala source
      Files.createDirectory(fixtureDir.resolve("src"))
      Files.write(fixtureDir.resolve("src/Main.scala"),
        """package com.example
          |
          |import java.util.List
          |
          |object Main {
          |  def hello(): String = "world"
          |}
          |
          |class Helper
          |""".stripMargin.getBytes)

      // Create Python source
      Files.write(fixtureDir.resolve("src/util.py"),
        """import os
          |from pathlib import Path
          |
          |class Util:
          |    def process(self):
          |        pass
          |""".stripMargin.getBytes)

      // Create JavaScript source
      Files.write(fixtureDir.resolve("src/app.js"),
        """import { helper } from './helper'
          |export class App {
          |  run() {}
          |}
          |""".stripMargin.getBytes)

      // Create Markdown file
      Files.write(fixtureDir.resolve("README.md"),
        """# Integration Test
          |
          |This is a test fixture.
          |""".stripMargin.getBytes)

      // Create JSON file
      Files.write(fixtureDir.resolve("config.json"),
        """{
          |  "name": "test",
          |  "version": "1.0"
          |}
          |""".stripMargin.getBytes)

      // Build config
      val config = DtrConfig(
        pathRoot = fixtureDir,
        outputPath = outputPath,
        maxChunkSize = 50000,
        chunkEnabled = true,
        noDotenv = false
      )

      // Create container and run
      val container = new Container()
      val env: Environment = dtrbuilder.infrastructure.environment.SystemEnvironment

      // Load .env
      val dotEnv = container.dotEnvLoader.load(config.pathRoot)
      dotEnv.vars.foreach { case (k, v) => env.set(k, v) }

      // Run the pipeline
      val result = container.dtrBuilderService.build(config, env)

      // Verify results
      result.fileCount should be > 0
      result.codexCount should be > 0
      result.typeCount should be >= 0
      result.chunkCount should be >= 1
      result.outputPaths should not be empty

      // Verify output file exists and has content
      result.outputPaths.foreach { p =>
        Files.exists(p) shouldBe true
        val content = Files.readString(p)
        content should include("META.GENERATOR=dtr-builder")
        content should include("META.ROOT=")
        content should include("FILE.")
        content should include("CODEX.")
      }

      // Verify we detected files from different languages
      val outputContent = result.outputPaths.map(p => Files.readString(p)).mkString("\n")
      outputContent should include("src/Main.scala")
      outputContent should include("src/util.py")
      outputContent should include("src/app.js")
      outputContent should include("README.md")
      outputContent should include("config.json")

    } finally {
      // Cleanup
      deleteRecursively(fixtureDir)
    }
  }

  it should "handle chunking when output exceeds max size" in {
    val fixtureDir = Files.createTempDirectory("dtr-chunk-")
    val outputPath = fixtureDir.resolve("chunked.itr")

    try {
      // Create many files to force chunking with a very small max size
      (1 to 20).foreach { i =>
        Files.write(fixtureDir.resolve(s"file$i.scala"),
          s"""package com.example
             |class File$i {
             |  def method$i(): Int = $i
             |}
             |""".stripMargin.getBytes)
      }

      val config = DtrConfig(
        pathRoot = fixtureDir,
        outputPath = outputPath,
        maxChunkSize = 100, // Very small — forces chunking
        chunkEnabled = true,
        noDotenv = true
      )

      val container = new Container()
      val result = container.dtrBuilderService.build(config, dtrbuilder.infrastructure.environment.SystemEnvironment)

      result.chunkCount should be > 1
      result.outputPaths should have size result.chunkCount

      // Verify chunk naming pattern
      result.outputPaths.foreach { p =>
        val filename = p.getFileName.toString
        filename should include("chunked")
      }

    } finally {
      deleteRecursively(fixtureDir)
    }
  }

  private def deleteRecursively(path: Path): Unit = {
    if (Files.isDirectory(path)) {
      Files.list(path).forEach(deleteRecursively)
    }
    Files.deleteIfExists(path)
  }
}
