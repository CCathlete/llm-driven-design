package dtrbuilder.infrastructure.ast

import dtrbuilder.application.SignatureExtractor
import dtrbuilder.domain.{CodexEntry, FileEntry}
import dtrbuilder.infrastructure.RegexSignatureExtractor
import dtrbuilder.infrastructure.ast.antlr.{JavaScriptLexer, JavaScriptParser}
import org.antlr.v4.runtime.{CharStreams, CommonTokenStream}
import org.antlr.v4.runtime.tree.ParseTreeWalker

/** AST extractor for JavaScript using ANTLR4 JavaScript grammar.
  *
  * Parses files with ANTLR4 JavaScriptLexer/JavaScriptParser.
  * Falls back to regex extraction on parse failure.
  *
  * Extracts: IMPORT, EXPORT, FUNCTION, CLASS, CONST/LET/VAR.
  */
class JavaScriptAstExtractor(regexFallback: RegexSignatureExtractor) extends SignatureExtractor {

  override def extract(entry: FileEntry): Seq[CodexEntry] = {
    tryAstExtraction(entry).getOrElse(regexFallback.extract(entry))
  }

  private def tryAstExtraction(entry: FileEntry): Option[Seq[CodexEntry]] = {
    try {
      val input = CharStreams.fromFileName(entry.absolutePath.toString)
      val lexer = new JavaScriptLexer(input)
      val tokens = new CommonTokenStream(lexer)
      val parser = new JavaScriptParser(tokens)
      parser.removeErrorListeners()
      val tree = parser.program()
      val listener = new JavaScriptAstListener(entry.relPath)
      new ParseTreeWalker().walk(listener, tree)
      Some(listener.result)
    } catch {
      case _: Exception =>
        None // Fall back to regex
    }
  }
}
