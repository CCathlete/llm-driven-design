package dtrbuilder.infrastructure

import dtrbuilder.domain.{Language, RegexPattern, SigType}
import scala.util.Try

/** Loads language profiles from classpath resources under src/main/resources/languages/.
  *
  * Each .properties file defines:
  *   name=LanguageName
  *   extensions=ext1,ext2,...
  *   shebangs=pat1,pat2,...            (optional, comma-separated)
  *   patterns=SIGTYPE:regex,SIGTYPE:regex,...  (comma-separated, colon-separated)
  *
  * Falls back to empty/default on missing files.
  */
class LanguageProfileLoader {

  /** Load all language profiles from the classpath. */
  def loadAll(): Seq[Language] = {
    val langNames = Seq(
      "scala", "java", "python", "javascript", "typescript",
      "markdown", "yaml", "json",
      "c", "cpp", "go", "rust", "ruby", "shell"
    )

    langNames.flatMap { name =>
      load(s"languages/$name.properties")
    }
  }

  /** Load a single language profile from a classpath resource path. */
  def load(resourcePath: String): Option[Language] = {
    val props = new java.util.Properties()
    Try {
      val stream = getClass.getClassLoader.getResourceAsStream(resourcePath)
      if (stream == null) return None
      try {
        props.load(stream)
      } finally {
        stream.close()
      }

      val name = props.getProperty("name")
      if (name == null || name.trim.isEmpty) return None

      val extensions = Option(props.getProperty("extensions"))
        .map(_.split(",").map(_.trim).toSeq)
        .getOrElse(Seq.empty)

      val shebangs = Option(props.getProperty("shebangs"))
        .filter(_.nonEmpty)
        .map(_.split(",").map(_.trim).toSeq)
        .getOrElse(Seq.empty)

      val patterns = Option(props.getProperty("patterns"))
        .map { patternsStr =>
          patternsStr.split(";").toSeq.flatMap { entry =>
            val colonIdx = entry.indexOf(':')
            if (colonIdx <= 0) None
            else {
              val sigTypeName = entry.substring(0, colonIdx).trim
              val regexStr = entry.substring(colonIdx + 1).trim
              SigType.fromString(sigTypeName).map { sigType =>
                RegexPattern(sigType, regexStr.r)
              }
            }
          }
        }
        .getOrElse(Seq.empty)

      Some(Language(name, extensions, shebangs, patterns))
    }.toOption.flatten
  }
}
