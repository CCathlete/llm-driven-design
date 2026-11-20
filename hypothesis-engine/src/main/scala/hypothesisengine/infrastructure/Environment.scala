package hypothesisengine.infrastructure

import java.nio.file.{Files, Path}

object DotEnv:
  def load(root: Path): Map[String, String] =
    val discovered: Option[Path] = walkUp(root.toAbsolutePath.normalize())
      .find(candidate => Files.isRegularFile(candidate.resolve(".env")))
    val raw: Map[String, String] = discovered.map(path => parse(path.resolve(".env"))).getOrElse(Map.empty)
    val env: Map[String, String] = raw ++ sys.env.toMap
    raw.map { case (key, value) => key -> expand(value, env, 0) }

  private def walkUp(current: Path): List[Path] =
    val parent: Path = current.getParent
    if parent == null then List(current) else current :: walkUp(parent)

  private def parse(path: Path): Map[String, String] =
    val lines: List[String] = scala.io.Source.fromFile(path.toFile).getLines().toList
    lines.flatMap { line =>
      val trimmed: String = line.trim
      if trimmed.isEmpty || trimmed.startsWith("#") then None
      else
        val eq: Int = trimmed.indexOf('=')
        if eq < 1 then None
        else Some(trimmed.substring(0, eq).trim -> trimmed.substring(eq + 1).trim)
    }.toMap

  private def expand(value: String, env: Map[String, String], depth: Int): String =
    if depth > 10 then throw new IllegalArgumentException("dotenv expansion depth exceeded")
    if value.length >= 2 && value.startsWith("'") && value.endsWith("'") then
      value.substring(1, value.length - 1)
    else
      val body: String =
        if value.length >= 2 && value.startsWith("\"") && value.endsWith("\"") then
          value.substring(1, value.length - 1)
        else value
      val pattern = "\\$\\{([A-Za-z_][A-Za-z0-9_]*)\\}".r
      pattern.replaceAllIn(body, matchResult =>
        val key: String = matchResult.group(1)
        env.getOrElse(key, throw new IllegalArgumentException("undefined dotenv variable " + key))
      )

final class SystemClock extends hypothesisengine.application.Clock:
  override def now(): java.time.Instant = java.time.Instant.now()