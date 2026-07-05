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
    val envVars = mutable.Map[String, String]()

    // System environment variables have precedence
    sys.env.foreach { case (k, v) => envVars(k) = v }

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
    sys.env.foreach { case (k, v) => resolvedEnv.updated(k, v) }
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

  private def parseValue(value: String): String = {
    if (value.startsWith("\'") && value.endsWith("\'")) {
      value.substring(1, value.length - 1) // Single quoted, literal
    } else if (value.startsWith("\"") && value.endsWith("\"")) {
      value.substring(1, value.length - 1) // Double quoted, needs expansion
    } else {
      value // Unquoted, needs expansion
    }
  }

  private def resolveExpansions(env: Map[String, String]): Map[String, String] = {
    val resolved = mutable.Map[String, String]()
    val resolving = mutable.Set[String]() // For cycle detection
    val cache = mutable.Map[String, String]()

    def resolveVar(key: String, depth: Int = 0): Option[String] = {
      if (depth > 10) {
        throw new IllegalStateException(s"Circular or too deep expansion detected for key: $key")
      }
      if (resolving.contains(key)) {
        throw new IllegalStateException(s"Circular reference detected for key: $key")
      }
      cache.get(key) match {
        case Some(value) => Some(value)
        case None =>
          env.get(key) match {
            case Some(rawValue) =>
              resolving.add(key)
              val expandedValue = varExpansionRegex.replaceAllIn(rawValue, m => {
                val refKey = m.group(1)
                resolveVar(refKey, depth + 1).getOrElse({
                  // If referenced variable is not found, leave as is or replace with empty string
                  // According to ITR, system env vars override .env values, and not finding .env is not an error.
                  // For missing internal references, we treat them as empty string as per common .env loader behavior.
                  System.err.println(s"Warning: Environment variable \'$refKey\' referenced in \'$key\' is not defined.")
                  ""
                })
              })
              resolving.remove(key)
              cache(key) = expandedValue
              Some(expandedValue)
            case None =>
              None
          }
      }
    }

    env.keys.foreach(key => resolveVar(key))
    env.map { case (key, _) => key -> resolveVar(key).getOrElse(env(key)) }
  }
}
