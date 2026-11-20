ThisBuild / scalaVersion := "3.3.3"
ThisBuild / organization := "hypothesisengine"

lazy val schemaDdl: String =
  "CREATE TABLE IF NOT EXISTS hypotheses (id TEXT PRIMARY KEY, statement TEXT, gate REAL, prior REAL);" +
  "CREATE TABLE IF NOT EXISTS coordinates (hypothesis_id TEXT, coordinate TEXT);" +
  "CREATE TABLE IF NOT EXISTS experiments (id TEXT PRIMARY KEY, hypothesis_id TEXT, definition TEXT, artifact_version TEXT);" +
  "CREATE TABLE IF NOT EXISTS executions (id TEXT PRIMARY KEY, hypothesis_id TEXT, experiment_id TEXT, artifact_version TEXT, command TEXT, revision TEXT, timeout_seconds INTEGER, exit_code INTEGER, stdout TEXT, stderr TEXT, duration_millis INTEGER, recorded_at TEXT);" +
  "CREATE TABLE IF NOT EXISTS evidence (execution_id TEXT, hypothesis_id TEXT, passed INTEGER, detail TEXT);" +
  "CREATE TABLE IF NOT EXISTS calculations (hypothesis_id TEXT, posterior REAL, calculated_at TEXT);" +
  "CREATE TABLE IF NOT EXISTS decisions (hypothesis_id TEXT, decision TEXT, gate REAL, posterior REAL)"

lazy val cleanLedger = taskKey[Unit]("Reset the embedded ledger for a fresh start")

lazy val root = (project in file(".")).settings(
  name := "hypothesis-engine",
  libraryDependencies ++= Seq(
    "org.xerial" % "sqlite-jdbc" % "3.45.3.0",
    "org.scalatest" %% "scalatest" % "3.2.18" % Test,
    "io.circe" %% "circe-core" % "0.14.10",
    "io.circe" %% "circe-generic" % "0.14.10",
    "io.circe" %% "circe-parser" % "0.14.10"
  ),
  cleanLedger := {
    val fresh: String = sys.props.getOrElse("hypothesis-engine.ledger.fresh", "true")
    val db: java.io.File = baseDirectory.value / "ledger.db"
    val suffixes: Seq[String] = Seq("", "-wal", "-shm")
    suffixes.foreach { suffix: String =>
      val target: java.io.File = new java.io.File(db.getAbsolutePath + suffix)
      if (target.exists()) target.delete()
    }
    val schema: java.io.File = baseDirectory.value / "src/main/resources/ledger-schema.sql"
    sbt.IO.write(schema, schemaDdl)
    println("LEDGER_CLEANED fresh=" + fresh)
  }
)