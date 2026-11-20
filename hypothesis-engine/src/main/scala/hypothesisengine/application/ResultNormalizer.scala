package hypothesisengine.application

import hypothesisengine.domain.*
import io.circe.Decoder
import io.circe.parser.decode

object ResultNormalizer:
  final case class Structured(passed: Boolean, detail: String)

  given Decoder[Structured] = Decoder.forProduct2("passed", "detail")(Structured.apply)

  def validate(raw: RawResult): Either[String, NormalizedResult] =
    val stdout: String = raw.stdout.trim
    val stderr: String = raw.stderr.trim
    val fromStdout: Either[String, Structured] = decode[Structured](stdout).left.map(_.getMessage)
    val fromStderr: Either[String, Structured] = decode[Structured](stderr).left.map(_.getMessage)
    if stdout.startsWith("{") && fromStdout.isLeft then
      Left("malformed structured result on stdout: " + fromStdout.left.getOrElse("unknown"))
    else if stderr.startsWith("{") && stdout.isEmpty && fromStderr.isLeft then
      Left("malformed structured result on stderr: " + fromStderr.left.getOrElse("unknown"))
    else
      fromStdout.orElse(fromStderr) match
        case Right(structured) =>
          Right(NormalizedResult(raw.executionId, raw.hypothesisId, structured.passed, structured.detail))
        case Left(_) =>
          Right(NormalizedResult(raw.executionId, raw.hypothesisId, raw.exitCode == 0, "exit=" + raw.exitCode.toString))