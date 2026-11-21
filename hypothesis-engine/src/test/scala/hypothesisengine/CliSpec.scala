package hypothesisengine

import hypothesisengine.control.Main
import java.nio.file.{Files, Path}
import java.sql.{Connection, ResultSet, Statement}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

final class CliSpec extends AnyFunSuite with Matchers:
  private val propertyName: String = "hypothesis-engine.ledger.path"

  private def withTempLedger(testBody: String => Unit): Unit =
    val previous: Option[String] = sys.props.get(propertyName)
    val file: Path = Files.createTempFile("feat19-ledger", ".db")
    Files.delete(file)
    sys.props(propertyName) = file.toString
    try testBody(file.toString)
    finally
      previous match
        case Some(value) => sys.props(propertyName) = value
        case None => sys.props.remove(propertyName)
      Files.deleteIfExists(file)

  private def open(dbPath: String): Connection =
    java.sql.DriverManager.getConnection("jdbc:sqlite:" + dbPath)

  private def scalar(dbPath: String, sql: String): Int =
    val conn: Connection = open(dbPath)
    try
      val statement: Statement = conn.createStatement()
      try
        val rows: ResultSet = statement.executeQuery(sql)
        try if rows.next() then rows.getInt(1) else 0 finally rows.close()
      finally statement.close()
    finally conn.close()

  private def seed(dbPath: String): Unit =
    val conn: Connection = open(dbPath)
    try
      val statement: Statement = conn.createStatement()
      try
        statement.execute("INSERT INTO hypotheses (id, statement, gate, prior, justification) VALUES ('H-A','a',0.85,0.5,'j')")
        statement.execute("INSERT INTO hypotheses (id, statement, gate, prior, justification) VALUES ('H-B','b',0.85,0.5,'j')")
        statement.execute("INSERT INTO coordinates (hypothesis_id, coordinate) VALUES ('H-A','C1')")
        statement.execute("INSERT INTO coordinates (hypothesis_id, coordinate) VALUES ('H-B','C2')")
      finally statement.close()
    finally conn.close()

  test("T9 init creates the schema and is idempotent") {
    withTempLedger { path =>
      Main.run(Array("init")) shouldBe 0
      scalar(path, "SELECT COUNT(*) FROM sqlite_master WHERE type='table'") shouldBe 7
      Main.run(Array("init")) shouldBe 0
      scalar(path, "SELECT COUNT(*) FROM sqlite_master WHERE type='table'") shouldBe 7
    }
  }

  test("T9 invalid subcommand arguments return exit code 2") {
    Main.run(Array("cleanup")) shouldBe 2
    Main.run(Array("frobnicate")) shouldBe 2
  }

  test("T10 the control layer contains no JDBC or DDL") {
    val mainSource: String = scala.io.Source.fromFile("src/main/scala/hypothesisengine/control/Main.scala").mkString
    mainSource.contains("java.sql") shouldBe false
    mainSource.contains("jdbc:sqlite") shouldBe false
    mainSource.contains("CREATE TABLE") shouldBe false
  }

  test("T11 cleanup by id removes only that hypothesis rows") {
    withTempLedger { path =>
      Main.run(Array("init")) shouldBe 0
      seed(path)
      Main.run(Array("cleanup", "H-A")) shouldBe 0
      scalar(path, "SELECT COUNT(*) FROM hypotheses WHERE id='H-A'") shouldBe 0
      scalar(path, "SELECT COUNT(*) FROM coordinates WHERE hypothesis_id='H-A'") shouldBe 0
      scalar(path, "SELECT COUNT(*) FROM hypotheses WHERE id='H-B'") shouldBe 1
      scalar(path, "SELECT COUNT(*) FROM coordinates WHERE hypothesis_id='H-B'") shouldBe 1
      scalar(path, "SELECT COUNT(*) FROM sqlite_master WHERE type='table'") shouldBe 7
    }
  }

  test("T12 cleanup --all empties tables and leaves the schema usable") {
    withTempLedger { path =>
      Main.run(Array("init")) shouldBe 0
      seed(path)
      Main.run(Array("cleanup", "--all")) shouldBe 0
      scalar(path, "SELECT COUNT(*) FROM hypotheses") shouldBe 0
      scalar(path, "SELECT COUNT(*) FROM coordinates") shouldBe 0
      scalar(path, "SELECT COUNT(*) FROM sqlite_master WHERE type='table'") shouldBe 7
      Main.run(Array("init")) shouldBe 0
    }
  }