package dtrbuilder.infrastructure

import dtrbuilder.application.DotEnvLoader
import dtrbuilder.domain.DotEnv
import java.nio.file.{Files, Path, Paths}
import scala.util.Try

/** Infrastructure adapter: discovers .env by walking up from ROOT_PATH.
  * Implements the DotEnvLoader port.
  *
  * Features:
  * - Walk up from root toward filesystem root, first .env wins
  * - Parse KEY=VALUE, ignore # comments and blank lines
  * - Unquoted, single-quoted, double-quoted values
  * - Single quotes prevent ${} expansion
  * - Double quotes allow ${} expansion
  * - Recursive ${} expansion up to max depth 10
  * - Self-references and circular refs produce hard error
  */
class FileSystemDotEnvLoader extends DotEnvLoader {

  import FileSystemDotEnvLoader._

  /** Discover .env by walking up from rootPath, parse and expand it. */
  override def load(rootPath: Path): DotEnv = {
    discoverEnvFile(rootPath) match {
      case Some(envPath) =>
        val rawVars = parseEnvFile(envPath)
        val expanded = expandAll(rawVars)
        DotEnv(vars = expanded, sourcePath = Some(envPath))
      case None =>
        DotEnv(vars = Map.empty, sourcePath = None)
    }
  }

  /** Walk up from start path toward /, looking for .env. */
  private def discoverEnvFile(start: Path): Option[Path] = {
    var current = start.toAbsolutePath.normalize
    val root = Paths.get("/").toAbsolutePath.normalize

    while (current != null) {
      val candidate = current.resolve(".env")
      if (Files.isRegularFile(candidate)) return Some(candidate)
      if (current == root) return None
      current = current.getParent
    }
    None
  }

  /** Parse a .env file into key-value pairs with quoting support. */
  private[infrastructure] def parseEnvFile(path: Path): Map[String, String] = {
    val lines = Try(Files.readAllLines(path)).getOrElse(java.util.Collections.emptyList())
    val vars = Map.newBuilder[String, String]

    lines.forEach { rawLine =>
      val line = rawLine.trim
      // Skip blank lines and comments
      if (line.nonEmpty && !line.startsWith("#")) {
        parseLine(line).foreach { case (key, value) =>
          vars += (key -> value)
        }
      }
    }

    vars.result()
  }

  /** Parse a single KEY=VALUE line with quoting support.
    * Supports: unquoted, 'single-quoted', "double-quoted"
    */
  private[infrastructure] def parseLine(line: String): Option[(String, String)] = {
    val eqIndex = line.indexOf('=')
    if (eqIndex <= 0) return None

    val key = line.substring(0, eqIndex).trim
    if (key.isEmpty || key.contains(" ")) return None

    val rawValue = line.substring(eqIndex + 1).trim

    val value = if (rawValue.startsWith("'") && rawValue.endsWith("'") && rawValue.length >= 2) {
      // Single-quoted: no expansion
      rawValue.substring(1, rawValue.length - 1)
    } else if (rawValue.startsWith("\"") && rawValue.endsWith("\"") && rawValue.length >= 2) {
      // Double-quoted: expansion allowed later
      rawValue.substring(1, rawValue.length - 1)
    } else {
      // Unquoted: strip inline comments but preserve # in values
      val noComment = rawValue.split("(?<!\\\\)#").headOption.getOrElse(rawValue).trim
      noComment
    }

    Some(key -> value)
  }

  /** Expand all ${VAR} references in values recursively up to maxDepth. */
  private[infrastructure] def expandAll(
      vars: Map[String, String],
      maxDepth: Int = MaxExpansionDepth
  ): Map[String, String] = {
    vars.map { case (key, value) =>
      key -> expandValue(key, value, vars, Set(key), 0, maxDepth)
    }
  }

  /** Expand ${VAR} in a single value, tracking visited keys for cycle detection. */
  private def expandValue(
      key: String,
      value: String,
      allVars: Map[String, String],
      visiting: Set[String],
      depth: Int,
      maxDepth: Int
  ): String = {
    if (depth > maxDepth) {
      throw new RuntimeException(
        s"Max expansion depth ($maxDepth) exceeded for key '$key'. " +
        s"Possible circular reference: ${visiting.mkString(" -> ")}"
      )
    }

    val varPattern: java.util.regex.Pattern = java.util.regex.Pattern.compile("""\$\{([^}]+)\}""")
    val matcher = varPattern.matcher(value)
    val sb = new StringBuffer()

    while (matcher.find()) {
      val varName = matcher.group(1).trim

      // Self-reference check
      if (varName == key) {
        throw new RuntimeException(
          "Self-reference detected: key '" + key + "' references itself (${" + varName + "})"
        )
      }

      // Cycle detection
      if (visiting.contains(varName)) {
        throw new RuntimeException(
          s"Circular reference detected: ${(visiting + varName).mkString(" -> ")}"
        )
      }

      // Look up the variable value
      val replacement = allVars.get(varName) match {
        case Some(resolvedValue) =>
          // Recursively expand the value
          expandValue(varName, resolvedValue, allVars, visiting + varName, depth + 1, maxDepth)
        case None =>
          // Variable not found — try system env
          sys.env.get(varName) match {
            case Some(systemValue) => systemValue
            case None =>
              // Leave unresolved as-is
              matcher.group(0)
          }
      }

      matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement))
    }
    matcher.appendTail(sb)
    sb.toString
  }
}

object FileSystemDotEnvLoader {
  val MaxExpansionDepth: Int = 10
}
