package hypothesisengine.infrastructure

import hypothesisengine.application.{Clock, ProcessRunner}
import hypothesisengine.domain.*
import io.circe.Decoder
import io.circe.parser.decode
import java.io.{BufferedReader, InputStreamReader}
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

object ExperimentDefinitionCodec:
  given Decoder[Runtime] = Decoder.decodeString.emap { value =>
    value match
      case "python" => Right(Runtime.Python)
      case "scala" => Right(Runtime.Scala)
      case other => Left("unsupported runtime " + other)
  }
  given Decoder[HypothesisId] = Decoder.decodeString.map(HypothesisId.apply)
  given Decoder[ExperimentDefinition] = Decoder.forProduct7(
    "experiment_id", "hypothesis_id", "runtime", "command",
    "working_directory", "timeout_seconds", "test_artifact_version"
  )(ExperimentDefinition.apply)

  def parse(json: String): Either[String, ExperimentDefinition] =
    decode[ExperimentDefinition](json).left.map(_.getMessage)

final class ProcessAdapter(clock: Clock) extends ProcessRunner:
  private def drain(reader: BufferedReader, buffer: StringBuilder): Unit =
    var line: String = reader.readLine()
    while line != null do
      buffer.append(line).append('\n')
      line = reader.readLine()

  override def run(definition: ExperimentDefinition): RawResult =
    val builder: ProcessBuilder = new ProcessBuilder(definition.command: _*)
    builder.directory(Paths.get(definition.workingDirectory).toFile)
    val process: Process = builder.start()
    val outBuffer: StringBuilder = new StringBuilder
    val errBuffer: StringBuilder = new StringBuilder
    val outThread: Thread = new Thread(() => drain(new BufferedReader(new InputStreamReader(process.getInputStream)), outBuffer))
    val errThread: Thread = new Thread(() => drain(new BufferedReader(new InputStreamReader(process.getErrorStream)), errBuffer))
    outThread.start()
    errThread.start()
    val startedAt: Long = System.nanoTime()
    val finished: Boolean = process.waitFor(definition.timeoutSeconds.toLong, TimeUnit.SECONDS)
    if !finished then process.destroyForcibly()
    outThread.join(1000L)
    errThread.join(1000L)
    val durationMillis: Long = (System.nanoTime() - startedAt) / 1000000L
    RawResult(
      executionId = ExecutionId(java.util.UUID.randomUUID().toString),
      hypothesisId = definition.hypothesisId,
      experimentId = definition.experimentId,
      testArtifactVersion = definition.testArtifactVersion,
      command = definition.command,
      environment = sys.env.toMap,
      sourceRevision = sys.env.getOrElse("SOURCE_REVISION", "unknown"),
      timeoutSeconds = definition.timeoutSeconds,
      exitCode = process.exitValue(),
      stdout = outBuffer.toString,
      stderr = errBuffer.toString,
      durationMillis = durationMillis,
      recordedAt = clock.now()
    )

object DecodeProbe:
  def main(args: Array[String]): Unit =
    val py: String = "{\"experiment_id\":\"EXP-001\",\"hypothesis_id\":\"H-001\",\"runtime\":\"python\",\"command\":[\"python\",\"-m\",\"pytest\"],\"working_directory\":\"./experiments/state\",\"timeout_seconds\":60,\"test_artifact_version\":\"v1\"}"
    val sc: String = "{\"experiment_id\":\"EXP-002\",\"hypothesis_id\":\"H-001\",\"runtime\":\"scala\",\"command\":[\"sbt\",\"testOnly\",\"StateConsistencySpec\"],\"working_directory\":\"./experiments/state-scala\",\"timeout_seconds\":120,\"test_artifact_version\":\"v1\"}"
    val bad: String = "{\"experiment_id\":\"EXP-003\",\"hypothesis_id\":\"H-001\",\"runtime\":\"ruby\",\"command\":[\"ruby\"],\"working_directory\":\".\",\"timeout_seconds\":5,\"test_artifact_version\":\"v1\"}"
    val pyOk: Boolean = ExperimentDefinitionCodec.parse(py).exists(_.runtime == Runtime.Python)
    val scOk: Boolean = ExperimentDefinitionCodec.parse(sc).exists(_.runtime == Runtime.Scala)
    val badRejected: Boolean = ExperimentDefinitionCodec.parse(bad).isLeft
    println((if pyOk then "PY_OK" else "PY_BAD") + " " + (if scOk then "SCALA_OK" else "SCALA_BAD") + " " + (if badRejected then "BAD_REJECTED" else "BAD_ACCEPTED"))