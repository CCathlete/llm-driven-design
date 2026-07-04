package dtrbuilder.infrastructure.ast

import dtrbuilder.domain.{CodexEntry, SigType}
import dtrbuilder.infrastructure.ast.antlr.{JavaScriptParser, JavaScriptParserBaseListener}

import scala.collection.mutable.ArrayBuffer

/** ANTLR ParseTreeListener for JavaScript that builds CodexEntry list.
  *
  * Extracts: IMPORT, EXPORT, FUNCTION, CLASS, CONST/LET/VAR, ARROW_FN.
  */
class JavaScriptAstListener(relPath: String) extends JavaScriptParserBaseListener {

  private val _result = ArrayBuffer.empty[CodexEntry]

  def result: Seq[CodexEntry] = _result.toSeq

  override def enterImportStatement(ctx: JavaScriptParser.ImportStatementContext): Unit = {
    _result += CodexEntry(relPath, SigType.IMPORT, ctx.getText)
  }

  override def enterExportDeclaration(ctx: JavaScriptParser.ExportDeclarationContext): Unit = {
    _result += CodexEntry(relPath, SigType.EXPORT, ctx.getText)
  }

  override def enterExportDefaultDeclaration(ctx: JavaScriptParser.ExportDefaultDeclarationContext): Unit = {
    _result += CodexEntry(relPath, SigType.EXPORT, ctx.getText)
  }

  override def enterFunctionDeclaration(ctx: JavaScriptParser.FunctionDeclarationContext): Unit = {
    val name = Option(ctx.identifier()).map(_.getText).getOrElse("<anonymous>")
    _result += CodexEntry(relPath, SigType.FUNCTION, s"function $name")
  }

  override def enterClassDeclaration(ctx: JavaScriptParser.ClassDeclarationContext): Unit = {
    val name = Option(ctx.identifier()).map(_.getText).getOrElse("<anonymous>")
    _result += CodexEntry(relPath, SigType.CLASS, s"class $name")
  }

  override def enterVariableStatement(ctx: JavaScriptParser.VariableStatementContext): Unit = {
    val vdl = ctx.variableDeclarationList()
    val modifier = Option(vdl.varModifier()).map(_.getText).getOrElse("var")
    (0 until vdl.variableDeclaration().size()).foreach { i =>
      val vd = vdl.variableDeclaration(i)
      val name = Option(vd.assignable()).flatMap(a => Option(a.identifier())).map(_.getText).getOrElse("<anonymous>")
      _result += CodexEntry(relPath, SigType.CONST, s"$modifier $name")
    }
  }
}
