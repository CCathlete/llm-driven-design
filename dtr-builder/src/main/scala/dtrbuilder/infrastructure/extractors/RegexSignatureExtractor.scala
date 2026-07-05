package dtrbuilder.infrastructure.extractors

import dtrbuilder.application.ports.SignatureExtractor
import dtrbuilder.domain.models.{CodexEntry, FileEntry, Language}
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class RegexSignatureExtractor extends SignatureExtractor {

  override def extract(entry: FileEntry): Seq[CodexEntry] = {
    entry.language match {
      case Some(lang) if lang.regexPatterns.nonEmpty =>
        try {
          val content = readFileContent(entry.absolutePath.toString)
          extractFromContent(entry.relPath, lang, content)
        } catch {
          case e: Exception =>
            System.err.println(s"Error extracting signatures from ${entry.relPath}: ${e.getMessage}")
            Seq.empty
        }
      case _ => Seq.empty
    }
  }

  def extractFromContent(relPath: String, language: Language, content: String): Seq[CodexEntry] = {
    val lines = content.split("\n")
    language.regexPatterns.flatMap { pattern =>
      lines.flatMap { line =>
        pattern.pattern.findFirstMatchIn(line).flatMap { m =>
          val sigText = (m.groupCount to 1 by -1).iterator
            .map(i => Option(m.group(i)))
            .find(_.isDefined)
            .flatten
            .getOrElse(line.trim)
          Some(CodexEntry(
            relPath = relPath,
            sigType = pattern.sigType,
            signatureText = sigText
          ))
        }
      }
    }
  }

  private def readFileContent(path: String): String = {
    val bytes = Files.readAllBytes(java.nio.file.Paths.get(path))
    new String(bytes, StandardCharsets.UTF_8)
  }
}
