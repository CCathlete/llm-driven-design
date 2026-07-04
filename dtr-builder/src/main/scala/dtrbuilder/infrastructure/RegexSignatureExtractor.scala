package dtrbuilder.infrastructure

import dtrbuilder.application.SignatureExtractor
import dtrbuilder.domain.{CodexEntry, FileEntry, Language}
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/** Infrastructure adapter: extracts code signatures using per-language regex pattern sets.
  * Operates on every supported language. Provides baseline coverage; AST extractors
  * (for Scala, Java, Python, JS, TS) provide deeper analysis when available.
  */
class RegexSignatureExtractor extends SignatureExtractor {

  /** Extract codex entries from a file using regex patterns of the file's language. */
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
      case _ => Seq.empty // No language detected or no patterns
    }
  }

  /** Extract codex entries from file content using the language's regex patterns. */
  def extractFromContent(relPath: String, language: Language, content: String): Seq[CodexEntry] = {
    val lines = content.split("\n")
    language.regexPatterns.flatMap { pattern =>
      lines.flatMap { line =>
        pattern.pattern.findFirstMatchIn(line).flatMap { m =>
          // Get the last non-null capturing group (typically the identifier name)
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

  /** Read file content as a UTF-8 string. */
  private def readFileContent(path: String): String = {
    val bytes = Files.readAllBytes(java.nio.file.Paths.get(path))
    new String(bytes, StandardCharsets.UTF_8)
  }
}
