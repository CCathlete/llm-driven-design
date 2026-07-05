package dtrbuilder.infrastructure

import dtrbuilder.infrastructure.loader.FileSystemDotEnvLoader
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.nio.file.{Files, Path}

/** Unit tests for FileSystemDotEnvLoader:
  * - Discovery walk-up (no .env, .env in root, .env in parent)
  * - Parsing (simple, quoted, comments)
  * - ${} expansion (simple, composite, nested, chain)
  * - Cycle detection (self-ref, mutual)
  * - Edge cases (missing var, empty value)
  */
class FileSystemDotEnvLoaderSpec extends AnyFlatSpec with Matchers {

  private val loader = new FileSystemDotEnvLoader

  // ─── Parse Line ───

  behavior of "FileSystemDotEnvLoader.parseLine"

  it should "parse simple KEY=VALUE" in {
    loader.parseLine("FOO=bar") should contain("FOO" -> "bar")
  }

  it should "parse KEY=VALUE with spaces around equals" in {
    loader.parseLine("FOO = bar") should contain("FOO" -> "bar")
  }

  it should "ignore blank lines and comments" in {
    loader.parseLine("") shouldBe None
    loader.parseLine("# comment") shouldBe None
    loader.parseLine("  # indented comment") shouldBe None
  }

  it should "strip single quotes" in {
    loader.parseLine("FOO='bar'") should contain("FOO" -> "bar")
  }

  it should "strip double quotes" in {
    loader.parseLine("FOO=\"bar\"") should contain("FOO" -> "bar")
  }

  it should "preserve content within quotes" in {
    loader.parseLine("FOO='bar baz'") should contain("FOO" -> "bar baz")
    loader.parseLine("FOO=\"bar baz\"") should contain("FOO" -> "bar baz")
  }

  it should "not expand ${} inside single quotes" in {
    loader.parseLine("FOO='${VAR}'") should contain("FOO" -> "${VAR}")
  }

  it should "return None for lines without equals" in {
    loader.parseLine("FOOBAR") shouldBe None
  }

  // ─── Parse Env File ───

  behavior of "FileSystemDotEnvLoader.parseEnvFile"

  it should "parse a .env file with mixed content" in {
    val tempDir = Files.createTempDirectory("dtr-dotenv-")
    try {
      val envFile = tempDir.resolve(".env")
      Files.write(envFile, """# Database config
                             |DB_HOST=localhost
                             |DB_PORT=5432
                             |DB_NAME=myapp
                             |
                             |# App config
                             |APP_ENV=development
                             |APP_DEBUG=true
                             |""".stripMargin.getBytes)

      val vars = loader.parseEnvFile(envFile)
      vars should contain.allOf(
        "DB_HOST" -> "localhost",
        "DB_PORT" -> "5432",
        "DB_NAME" -> "myapp",
        "APP_ENV" -> "development",
        "APP_DEBUG" -> "true"
      )
      vars should have size 5
    } finally {
      deleteRecursively(tempDir)
    }
  }

  it should "parse quoted values" in {
    val tempDir = Files.createTempDirectory("dtr-dotenv-")
    try {
      val envFile = tempDir.resolve(".env")
      Files.write(envFile, """UNQUOTED=value
                             |SINGLE='single value'
                             |DOUBLE="double value"
                             |""".stripMargin.getBytes)

      val vars = loader.parseEnvFile(envFile)
      vars("UNQUOTED") shouldBe "value"
      vars("SINGLE") shouldBe "single value"
      vars("DOUBLE") shouldBe "double value"
    } finally {
      deleteRecursively(tempDir)
    }
  }

  // ─── Expand All ───

  behavior of "FileSystemDotEnvLoader.expandAll"

  it should "expand simple ${VAR} references" in {
    val vars = Map("NAME" -> "world", "GREETING" -> "Hello, ${NAME}")
    val expanded = loader.expandAll(vars)
    expanded("GREETING") shouldBe "Hello, world"
  }

  it should "expand composite references" in {
    val vars = Map(
      "BASE" -> "/usr/local",
      "MODULE" -> "myapp",
      "PATH" -> "${BASE}/src/${MODULE}/main"
    )
    val expanded = loader.expandAll(vars)
    expanded("PATH") shouldBe "/usr/local/src/myapp/main"
  }

  it should "expand nested references" in {
    val vars = Map(
      "A" -> "hello",
      "B" -> "${A}",
      "C" -> "${B}"
    )
    val expanded = loader.expandAll(vars)
    expanded("C") shouldBe "hello"
  }

  it should "handle chain expansion" in {
    val vars = Map(
      "VER" -> "1.0",
      "ARTIFACT" -> "mylib",
      "JAR" -> "${ARTIFACT}-${VER}.jar"
    )
    val expanded = loader.expandAll(vars)
    expanded("JAR") shouldBe "mylib-1.0.jar"
  }

  it should "keep unresolved references as-is" in {
    val vars = Map("MSG" -> "Hello, ${UNKNOWN}")
    val expanded = loader.expandAll(vars)
    expanded("MSG") shouldBe "Hello, ${UNKNOWN}"
  }

  it should "handle empty values" in {
    val vars = Map("EMPTY" -> "", "REF" -> "${EMPTY}")
    val expanded = loader.expandAll(vars)
    expanded("REF") shouldBe ""
  }

  // ─── Cycle Detection ───

  behavior of "FileSystemDotEnvLoader cycle detection"

  it should "throw on self-reference" in {
    val vars = Map("X" -> "${X}")
    an[RuntimeException] should be thrownBy loader.expandAll(vars)
  }

  it should "throw on circular reference" in {
    val vars = Map("A" -> "${B}", "B" -> "${A}")
    an[RuntimeException] should be thrownBy loader.expandAll(vars)
  }

  it should "throw on longer circular chain" in {
    val vars = Map("A" -> "${B}", "B" -> "${C}", "C" -> "${A}")
    an[RuntimeException] should be thrownBy loader.expandAll(vars)
  }

  // ─── Discovery (walk-up) ───

  behavior of "FileSystemDotEnvLoader discovery"

  it should "find .env in root directory" in {
    val tempDir = Files.createTempDirectory("dtr-dotenv-")
    try {
      Files.write(tempDir.resolve(".env"), "ROOT_VAR=found".getBytes)

      val result = loader.load(tempDir)
      result.sourcePath shouldBe defined
      result.get("ROOT_VAR") should contain("found")
    } finally {
      deleteRecursively(tempDir)
    }
  }

  it should "find .env in parent directory" in {
    val tempDir = Files.createTempDirectory("dtr-dotenv-")
    try {
      Files.write(tempDir.resolve(".env"), "PARENT_VAR=found".getBytes)
      val subDir = tempDir.resolve("subdir")
      Files.createDirectory(subDir)

      val result = loader.load(subDir)
      result.sourcePath shouldBe defined
      result.get("PARENT_VAR") should contain("found")
    } finally {
      deleteRecursively(tempDir)
    }
  }

  it should "return empty DotEnv when no .env exists" in {
    val tempDir = Files.createTempDirectory("dtr-dotenv-")
    try {
      val result = loader.load(tempDir)
      result.sourcePath shouldBe None
      result.vars shouldBe empty
    } finally {
      deleteRecursively(tempDir)
    }
  }

  it should "load .env with expansion" in {
    val tempDir = Files.createTempDirectory("dtr-dotenv-")
    try {
      Files.write(tempDir.resolve(".env"),
        """BASE=/app
          |MODULE=core
          |PATH=${BASE}/${MODULE}/lib
          |""".stripMargin.getBytes)

      val result = loader.load(tempDir)
      result.get("PATH") should contain("/app/core/lib")
    } finally {
      deleteRecursively(tempDir)
    }
  }

  private def deleteRecursively(path: Path): Unit = {
    if (Files.isDirectory(path)) {
      Files.list(path).forEach(deleteRecursively)
    }
    Files.deleteIfExists(path)
  }
}
