package dtrbuilder.infrastructure.ast

import dtrbuilder.application.ports.SignatureExtractor
import dtrbuilder.domain.models.{CodexEntry, FileEntry}
import dtrbuilder.infrastructure.extractors.RegexSignatureExtractor
import dtrbuilder.infrastructure.ast.antlr.{Python3Lexer, Python3Parser}
import org.antlr.v4.runtime.{CharStreams, CommonTokenStream}
import org.antlr.v4.runtime.tree.ParseTreeWalker

/** AST extractor for Python using ANTLR4 Python3 grammar.
  *
  * Parses files with ANTLR4 Python3Lexer/Python3Parser.
  * Falls back to regex extraction on parse failure.
  *
  * Extracts: IMPORT, FROM_IMPORT, CLASS, FUNCTION (including decorated).
  */
class PythonAstExtractor(regexFallback: RegexSignatureExtractor) extends SignatureExtractor {

  override def extract(entry: FileEntry): Seq[CodexEntry] = {
    tryAstExtraction(entry).getOrElse(regexFallback.extract(entry))
  }

  private def tryAstExtraction(entry: FileEntry): Option[Seq[CodexEntry]] = {
    try {
      val input = CharStreams.fromFileName(entry.absolutePath.toString)
      val lexer = new Python3Lexer(input)
      val tokens = new CommonTokenStream(lexer)
      val parser = new Python3Parser(tokens)
      // Remove the default console error listener to suppress parse errors
      parser.removeErrorListeners()
      val tree = parser.file_input()
      val listener = new Python3AstListener(entry.relPath)
      new ParseTreeWalker().walk(listener, tree)
      Some(listener.result)
    } catch {
      case _: Exception =>
        None // Fall back to regex
    }
  }
}
