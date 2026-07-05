package dtrbuilder.infrastructure.detection

import dtrbuilder.domain.models.{Language, RegexPattern, SigType}
import scala.util.Try

class ClasspathLanguageProfileLoader extends dtrbuilder.application.ports.LanguageProfileLoader {

  override def loadAll(): Seq[Language] = {
    val langNames = Seq(
      "scala", "java", "python", "javascript", "typescript",
      "markdown", "yaml", "json",
      "c", "cpp", "go", "rust", "ruby", "shell"
    )

    langNames.flatMap { name =>
      load(s"languages/$name.properties")
    }
  }

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
