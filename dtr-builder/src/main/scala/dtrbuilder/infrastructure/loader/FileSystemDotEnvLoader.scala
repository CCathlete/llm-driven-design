package dtrbuilder.infrastructure.loader

import dtrbuilder.application.ports.DotEnvLoader
import dtrbuilder.domain.models.DotEnv
import java.nio.file.{Files, Path, Paths}
import scala.util.Try

class FileSystemDotEnvLoader extends DotEnvLoader {

  import FileSystemDotEnvLoader._

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

  private[infrastructure] def parseEnvFile(path: Path): Map[String, String] = {
    val lines = Try(Files.readAllLines(path)).getOrElse(java.util.Collections.emptyList())
    val vars = Map.newBuilder[String, String]

    lines.forEach { rawLine =>
      val line = rawLine.trim
      if (line.nonEmpty && !line.startsWith("#")) {
        parseLine(line).foreach { case (key, value) =>
          vars += (key -> value)
        }
      }
    }

    vars.result()
  }

  private[infrastructure] def parseLine(line: String): Option[(String, String)] = {
    val eqIndex = line.indexOf('=')
    if (eqIndex <= 0) return None

    val key = line.substring(0, eqIndex).trim
    if (key.isEmpty || key.contains(" ")) return None

    val rawValue = line.substring(eqIndex + 1).trim

    val value = if (rawValue.startsWith("'") && rawValue.endsWith("'") && rawValue.length >= 2) {
      rawValue.substring(1, rawValue.length - 1)
    } else if (rawValue.startsWith("\"") && rawValue.endsWith("\"") && rawValue.length >= 2) {
      rawValue.substring(1, rawValue.length - 1)
    } else {
      val noComment = rawValue.split("(?<!\\\\)#").headOption.getOrElse(rawValue).trim
      noComment
    }

    Some(key -> value)
  }

  private[infrastructure] def expandAll(
      vars: Map[String, String],
      maxDepth: Int = MaxExpansionDepth
  ): Map[String, String] = {
    vars.map { case (key, value) =>
      key -> expandValue(key, value, vars, Set(key), 0, maxDepth)
    }
  }

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

      if (varName == key) {
        throw new RuntimeException(
          "Self-reference detected: key '" + key + "' references itself (${" + varName + "})"
        )
      }

      if (visiting.contains(varName)) {
        throw new RuntimeException(
          s"Circular reference detected: ${(visiting + varName).mkString(" -> ")}"
        )
      }

      val replacement = allVars.get(varName) match {
        case Some(resolvedValue) =>
          expandValue(varName, resolvedValue, allVars, visiting + varName, depth + 1, maxDepth)
        case None =>
          sys.env.get(varName) match {
            case Some(systemValue) => systemValue
            case None =>
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
