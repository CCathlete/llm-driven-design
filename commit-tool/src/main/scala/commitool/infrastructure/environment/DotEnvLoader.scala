package commitool.infrastructure.environment

import scala.io.Source
import java.nio.file.{Path, Paths, Files}
import scala.collection.mutable
import scala.util.matching.Regex
import scala.util.control.NonFatal

object DotEnvLoader {

  private val keyValueRegex: Regex = "^\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*=\\s*(.*)\\s*$".r
  private val varExpansionRegex: Regex = "\\$\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}".r

  def load(): Map[String, String] = {
    val envVars = mutable.Map[String, ValueType]()

    // System environment variables have precedence
    sys.env.foreach { case (k, v) => envVars(k) = Expandable(v) }

    findDotEnvFile().foreach {
      filePath =>
        try {
          Source.fromFile(filePath.toFile).getLines().foreach {
            case keyValueRegex(key, value) =>
              // Only add if not already present from system env
              if (!envVars.contains(key)) {
                envVars(key) = parseValue(value)
              }
            case _ => // Ignore lines that don't match key=value pattern
          }
        } catch {
          case NonFatal(e) => System.err.println(s"Warning: Could not read .env file at $filePath: ${e.getMessage}")
        }
    }

    val resolvedEnv = resolveExpansions(envVars.toMap)
    resolvedEnv // System env vars should override after expansion too
  }

  private def findDotEnvFile(): Option[Path] = {
    var currentPath: Path = Paths.get(System.getProperty("user.dir")).toAbsolutePath
    while (currentPath != null) {
      val dotEnvPath = currentPath.resolve(".env")
      if (Files.exists(dotEnvPath) && Files.isRegularFile(dotEnvPath)) {
        return Some(dotEnvPath)
      }
      currentPath = currentPath.getParent
    }
    None
  }

private sealed trait ValueType
  private case class Literal(value: String) extends ValueType
  private case class Expandable(value: String) extends ValueType

  private def parseValue(value: String): ValueType = {
    if (value.startsWith("'") && value.endsWith("'")) {
      Literal(value.substring(1, value.length - 1))
    } else if (value.startsWith("\"") && value.endsWith("\"")) {
      Expandable(value.substring(1, value.length - 1))
    } else {
      Expandable(value.trim)
    }
  }

  private def resolveExpansions(raw: Map[String, ValueType]): Map[String, String] = {
    val resolving = mutable.Set[String]()
    val cache = mutable.Map[String, String]()

    def resolveVar(key: String, depth: Int): Option[String] = {
      if (depth > 10)
        throw new IllegalStateException(s"Circular or too deep expansion detected for key: $key")
      if (resolving.contains(key))
        throw new IllegalStateException(s"Circular reference detected for key: $key")

      cache.get(key).orElse {
        raw.get(key) match {
          case Some(Expandable(rawValue)) =>
            resolving.add(key)
            val expanded = varExpansionRegex.replaceAllIn(rawValue, m => {
              val refKey = m.group(1)
              resolveVar(refKey, depth + 1).getOrElse {
                System.err.println(s"Warning: Environment variable '$refKey' referenced in '$key' is not defined.")
                ""
              }
            })
            resolving.remove(key)
            cache(key) = expanded
            Some(expanded)
          case Some(Literal(value)) => Some(value) // literal: no expansion
          case None => None
        }
      }
    }

    raw.keys.foreach(key => resolveVar(key, 0))
    raw.map { case (key, _) => key -> resolveVar(key, 0).getOrElse(throw new IllegalStateException(s"Unresolvable key: $key")) }
  }
}
