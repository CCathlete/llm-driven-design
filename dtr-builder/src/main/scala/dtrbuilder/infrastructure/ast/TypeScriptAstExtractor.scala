package dtrbuilder.infrastructure.ast

import dtrbuilder.application.SignatureExtractor
import dtrbuilder.domain.{CodexEntry, FileEntry}
import dtrbuilder.infrastructure.RegexSignatureExtractor
import dtrbuilder.infrastructure.ast.antlr.{TypeScriptLexer, TypeScriptParser}
import org.antlr.v4.runtime.{CharStreams, CommonTokenStream}
import org.antlr.v4.runtime.tree.ParseTreeWalker

/** AST extractor for TypeScript using ANTLR4 TypeScript grammar.
  *
  * Parses files with ANTLR4 TypeScriptLexer/TypeScriptParser.
  * Falls back to regex extraction on parse failure.
  *
  * Extracts: IMPORT, EXPORT, INTERFACE, TYPE, CLASS, FUNCTION,
  *           CONST, ENUM, DECORATOR.
  */
class TypeScriptAstExtractor(regexFallback: RegexSignatureExtractor) extends SignatureExtractor {

  override def extract(entry: FileEntry): Seq[CodexEntry] = {
    tryAstExtraction(entry).getOrElse(regexFallback.extract(entry))
  }

  private def tryAstExtraction(entry: FileEntry): Option[Seq[CodexEntry]] = {
    try {
      val input = CharStreams.fromFileName(entry.absolutePath.toString)
      val lexer = new TypeScriptLexer(input)
      val tokens = new CommonTokenStream(lexer)
      val parser = new TypeScriptParser(tokens)
      parser.removeErrorListeners()
      val tree = parser.program()
      val listener = new TypeScriptAstListener(entry.relPath)
      new ParseTreeWalker().walk(listener, tree)
      Some(listener.result)
    } catch {
      case _: Exception =>
        None // Fall back to regex
    }
  }
}
