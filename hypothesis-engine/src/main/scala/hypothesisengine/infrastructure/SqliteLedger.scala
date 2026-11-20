package hypothesisengine.infrastructure

import hypothesisengine.application.Ledger
import hypothesisengine.domain.*
import java.nio.file.{Files, Paths}
import java.sql.{Connection, DriverManager, PreparedStatement}
import java.time.Instant
import scala.util.{Failure, Success, Using}

final class SqliteLedger(dbPath: String = "ledger.db") extends Ledger:
  private val schema: String =
    Option(getClass.getResourceAsStream("/ledger-schema.sql")) match
      case Some(stream) => scala.io.Source.fromInputStream(stream).mkString
      case None => ""

  private def connection: Connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath)

  initializeSchema()

  private def initializeSchema(): Unit =
    val attempt = Using.Manager { use =>
      val conn: Connection = use(connection)
      val statement = use(conn.createStatement())
      schema.split(";").foreach { part =>
        if part.trim.nonEmpty then statement.execute(part)
      }
    }
    attempt match
      case Failure(error) => throw error
      case Success(_) => ()

  private def write(sql: String)(bind: PreparedStatement => Unit): Either[String, Unit] =
    Using.Manager { use =>
      val conn: Connection = use(connection)
      val statement: PreparedStatement = use(conn.prepareStatement(sql))
      bind(statement)
      statement.executeUpdate()
    } match
      case Success(_) => Right(())
      case Failure(error) => Left(error.getMessage)

  private def count(sql: String)(bind: PreparedStatement => Unit): Int =
    Using.Manager { use =>
      val conn: Connection = use(connection)
      val statement: PreparedStatement = use(conn.prepareStatement(sql))
      bind(statement)
      val resultSet = use(statement.executeQuery())
      if resultSet.next() then resultSet.getInt(1) else 0
    }.getOrElse(0)

  override def saveRaw(result: RawResult): Either[String, Unit] =
    write("INSERT OR REPLACE INTO executions (id, hypothesis_id, experiment_id, artifact_version, command, revision, timeout_seconds, exit_code, stdout, stderr, duration_millis, recorded_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)") { statement =>
      statement.setString(1, ExecutionId.value(result.executionId))
      statement.setString(2, HypothesisId.value(result.hypothesisId))
      statement.setString(3, result.experimentId)
      statement.setString(4, result.testArtifactVersion)
      statement.setString(5, result.command.mkString(" "))
      statement.setString(6, result.sourceRevision)
      statement.setInt(7, result.timeoutSeconds)
      statement.setInt(8, result.exitCode)
      statement.setString(9, result.stdout)
      statement.setString(10, result.stderr)
      statement.setLong(11, result.durationMillis)
      statement.setString(12, result.recordedAt.toString)
    }

  override def saveNormalized(result: NormalizedResult): Either[String, Unit] =
    write("INSERT OR REPLACE INTO evidence (execution_id, hypothesis_id, passed, detail) VALUES (?, ?, ?, ?)") { statement =>
      statement.setString(1, ExecutionId.value(result.executionId))
      statement.setString(2, HypothesisId.value(result.hypothesisId))
      statement.setInt(3, if result.passed then 1 else 0)
      statement.setString(4, result.detail)
    }

  override def saveCalculation(calculation: ProbabilityCalculation): Either[String, Unit] =
    write("INSERT INTO calculations (hypothesis_id, posterior, calculated_at) VALUES (?, ?, ?)") { statement =>
      statement.setString(1, HypothesisId.value(calculation.hypothesisId))
      statement.setDouble(2, calculation.posterior)
      statement.setString(3, calculation.calculatedAt.toString)
    }

  override def saveDecision(hypothesisId: HypothesisId, decision: Decision): Either[String, Unit] =
    val label: String = decision match
      case Decision.Green(_, _, _) => "GREEN"
      case Decision.Red(_, _, _, _, _) => "RED"
      case Decision.Blocked(_) => "BLOCKED"
    val gate: Double = decision match
      case Decision.Green(_, _, value) => value
      case Decision.Red(_, _, value, _, _) => value
      case Decision.Blocked(_) => 0.0d
    val posterior: Double = decision match
      case Decision.Green(_, value, _) => value
      case Decision.Red(_, value, _, _, _) => value
      case Decision.Blocked(_) => 0.0d
    write("INSERT INTO decisions (hypothesis_id, decision, gate, posterior) VALUES (?, ?, ?, ?)") { statement =>
      statement.setString(1, HypothesisId.value(hypothesisId))
      statement.setString(2, label)
      statement.setDouble(3, gate)
      statement.setDouble(4, posterior)
    }

  override def saveHypothesis(hypothesis: Hypothesis): Either[String, Unit] =
    write("INSERT OR REPLACE INTO hypotheses (id, statement, gate, prior) VALUES (?, ?, ?, ?)") { statement =>
      statement.setString(1, HypothesisId.value(hypothesis.id))
      statement.setString(2, hypothesis.statement)
      statement.setDouble(3, hypothesis.gate)
      statement.setDouble(4, hypothesis.prior)
    }

  override def saveCoordinates(hypothesisId: HypothesisId, coordinates: Vector[DtrCoordinate]): Either[String, Unit] =
    write("DELETE FROM coordinates WHERE hypothesis_id = ?") { statement =>
      statement.setString(1, HypothesisId.value(hypothesisId))
    }.flatMap { _ =>
      coordinates.foldLeft[Either[String, Unit]](Right(())) { (acc, coordinate) =>
        acc.flatMap { _ =>
          write("INSERT INTO coordinates (hypothesis_id, coordinate) VALUES (?, ?)") { statement =>
            statement.setString(1, HypothesisId.value(hypothesisId))
            statement.setString(2, DtrCoordinate.value(coordinate))
          }
        }
      }
    }

  override def saveExperiments(hypothesisId: HypothesisId, definitions: Vector[ExperimentDefinition]): Either[String, Unit] =
    write("DELETE FROM experiments WHERE hypothesis_id = ?") { statement =>
      statement.setString(1, HypothesisId.value(hypothesisId))
    }.flatMap { _ =>
      definitions.foldLeft[Either[String, Unit]](Right(())) { (acc, definition) =>
        acc.flatMap { _ =>
          write("INSERT OR REPLACE INTO experiments (id, hypothesis_id, definition, artifact_version) VALUES (?, ?, ?, ?)") { statement =>
            statement.setString(1, definition.experimentId)
            statement.setString(2, HypothesisId.value(definition.hypothesisId))
            statement.setString(3, definition.command.mkString(" "))
            statement.setString(4, definition.testArtifactVersion)
          }
        }
      }
    }

  override def missingFor(hypothesis: Hypothesis, experiments: Vector[ExperimentDefinition]): Vector[String] =
    val id: String = HypothesisId.value(hypothesis.id)
    val missing: Vector[String] = Vector(
      if count("SELECT COUNT(*) FROM hypotheses WHERE id = ?")(statement => statement.setString(1, id)) == 0 then "hypothesis record" else "",
      if count("SELECT COUNT(*) FROM coordinates WHERE hypothesis_id = ?")(statement => statement.setString(1, id)) == 0 then "DTR coordinate references" else "",
      if count("SELECT COUNT(*) FROM experiments WHERE hypothesis_id = ?")(statement => statement.setString(1, id)) == 0 then "experiment definition" else "",
      if experiments.isEmpty then "probability calculation inputs" else ""
    ).filter(_.nonEmpty)
    val hasExecution: Boolean = count("SELECT COUNT(*) FROM executions WHERE hypothesis_id = ?")(statement => statement.setString(1, id)) > 0
    val hasEvidence: Boolean = count("SELECT COUNT(*) FROM evidence WHERE hypothesis_id = ?")(statement => statement.setString(1, id)) > 0
    missing ++ (if hasExecution then Vector.empty[String] else Vector("execution with reproducibility metadata")) ++ (if hasEvidence then Vector.empty[String] else Vector("raw evidence"))

  override def resultsFor(hypothesisId: HypothesisId): Vector[(RawResult, NormalizedResult)] =
    val id: String = HypothesisId.value(hypothesisId)
    Using.Manager { use =>
      val conn: Connection = use(connection)
      val statement: PreparedStatement = use(conn.prepareStatement("SELECT e.id, e.experiment_id, e.artifact_version, e.command, e.revision, e.timeout_seconds, e.exit_code, e.stdout, e.stderr, e.duration_millis, e.recorded_at, v.passed, v.detail FROM executions e JOIN evidence v ON v.execution_id = e.id WHERE e.hypothesis_id = ?"))
      statement.setString(1, id)
      val resultSet = use(statement.executeQuery())
      val buffer = Vector.newBuilder[(RawResult, NormalizedResult)]
      while resultSet.next() do
        val executionId: ExecutionId = ExecutionId(resultSet.getString(1))
        val raw: RawResult = RawResult(
          executionId = executionId,
          hypothesisId = hypothesisId,
          experimentId = resultSet.getString(2),
          testArtifactVersion = resultSet.getString(3),
          command = resultSet.getString(4).split(" ").toVector,
          sourceRevision = resultSet.getString(5),
          timeoutSeconds = resultSet.getInt(6),
          exitCode = resultSet.getInt(7),
          stdout = resultSet.getString(8),
          stderr = resultSet.getString(9),
          durationMillis = resultSet.getLong(10),
          recordedAt = Instant.parse(resultSet.getString(11))
        )
        val normalized: NormalizedResult = NormalizedResult(executionId, hypothesisId, resultSet.getInt(12) == 1, resultSet.getString(13))
        buffer += ((raw, normalized))
      buffer.result()
    }.getOrElse(Vector.empty[(RawResult, NormalizedResult)])