ThisBuild / scalaVersion := "3.3.3"
ThisBuild / organization := "hypothesisengine"

lazy val schemaDdl: String =
  "CREATE TABLE IF NOT EXISTS hypotheses (id TEXT PRIMARY KEY, statement TEXT, gate REAL, prior REAL, justification TEXT);" + "\n" +
  "CREATE TABLE IF NOT EXISTS coordinates (hypothesis_id TEXT, coordinate TEXT);" + "\n" +
  "CREATE TABLE IF NOT EXISTS experiments (id TEXT PRIMARY KEY, hypothesis_id TEXT, definition TEXT, artifact_version TEXT);" + "\n" +
  "CREATE TABLE IF NOT EXISTS executions (id TEXT PRIMARY KEY, hypothesis_id TEXT, experiment_id TEXT, artifact_version TEXT, command TEXT, revision TEXT, timeout_seconds INTEGER, exit_code INTEGER, stdout TEXT, stderr TEXT, duration_millis INTEGER, recorded_at TEXT);" + "\n" +
  "CREATE TABLE IF NOT EXISTS evidence (execution_id TEXT, hypothesis_id TEXT, passed INTEGER, detail TEXT);" + "\n" +
  "CREATE TABLE IF NOT EXISTS calculations (hypothesis_id TEXT, posterior REAL, calculated_at TEXT);" + "\n" +
  "CREATE TABLE IF NOT EXISTS decisions (hypothesis_id TEXT, decision TEXT, gate REAL, posterior REAL)"

lazy val cleanLedger = taskKey[Unit]("Reset the embedded ledger for a fresh start")
lazy val ensureLedger = taskKey[Unit]("Create ledger.db from the embedded schema when absent")

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
  },
  ensureLedger := {
    Class.forName("org.sqlite.JDBC")
    val db: java.io.File = baseDirectory.value / "ledger.db"
    if (db.exists()) {
      println("LEDGER_EXISTS " + db.getAbsolutePath)
    } else {
      val conn: java.sql.Connection =
        java.sql.DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath)
      try {
        val stmt: java.sql.Statement = conn.createStatement()
        try {
          schemaDdl.split(";").foreach { part: String =>
            if (part.trim.nonEmpty) stmt.execute(part)
          }
        } finally stmt.close()
        println("LEDGER_CREATED " + db.getAbsolutePath)
      } finally conn.close()
    }
  },
  Compile / compile := ((Compile / compile) dependsOn ensureLedger).value,

  Compile / mainClass := Some("hypothesisengine.control.Main"),
  assembly / assemblyJarName := "hypothesis-engine.jar",
  assembly / assemblyOutputPath := baseDirectory.value / ".." / ".LLMDD" / "tools" / "hypothesis-engine.jar",
  assembly / assemblyMergeStrategy := {
    case "META-INF/services/java.sql.Driver" => MergeStrategy.concat
    case PathList("META-INF", "MANIFEST.MF")   => MergeStrategy.discard
    case PathList("META-INF", "DEPENDENCIES")  => MergeStrategy.discard
    case PathList("META-INF", "LICENSE")       => MergeStrategy.discard
    case PathList("META-INF", "LICENSE.txt")   => MergeStrategy.discard
    case PathList("META-INF", "NOTICE")        => MergeStrategy.discard
    case PathList("META-INF", "NOTICE.txt")    => MergeStrategy.discard
    case PathList("META-INF", xs @ _*)         => MergeStrategy.first
    case _                                     => MergeStrategy.first
  }
)