package dtrbuilder.infrastructure.ast

import dtrbuilder.domain.{CodexEntry, SigType}
import dtrbuilder.infrastructure.ast.antlr.{TypeScriptParser, TypeScriptParserBaseListener}

import scala.collection.mutable.ArrayBuffer

/** ANTLR ParseTreeListener for TypeScript that builds CodexEntry list.
  *
  * Extracts: IMPORT, EXPORT, INTERFACE, TYPE, CLASS, FUNCTION, CONST, ENUM, DECORATOR.
  */
class TypeScriptAstListener(relPath: String) extends TypeScriptParserBaseListener {

  private val _result = ArrayBuffer.empty[CodexEntry]

  def result: Seq[CodexEntry] = _result.toSeq

  override def enterImportStatement(ctx: TypeScriptParser.ImportStatementContext): Unit = {
    _result += CodexEntry(relPath, SigType.IMPORT, ctx.getText)
  }

  override def enterExportStatement(ctx: TypeScriptParser.ExportStatementContext): Unit = {
    _result += CodexEntry(relPath, SigType.EXPORT, ctx.getText)
  }

  override def enterInterfaceDeclaration(ctx: TypeScriptParser.InterfaceDeclarationContext): Unit = {
    val name = Option(ctx.identifier()).map(_.getText).getOrElse("<anonymous>")
    _result += CodexEntry(relPath, SigType.INTERFACE, s"interface $name")
  }

  override def enterTypeAliasDeclaration(ctx: TypeScriptParser.TypeAliasDeclarationContext): Unit = {
    val name = Option(ctx.identifier()).map(_.getText).getOrElse("<anonymous>")
    _result += CodexEntry(relPath, SigType.TYPE, s"type $name")
  }

  override def enterEnumDeclaration(ctx: TypeScriptParser.EnumDeclarationContext): Unit = {
    val name = Option(ctx.identifier()).map(_.getText).getOrElse("<anonymous>")
    _result += CodexEntry(relPath, SigType.ENUM, s"enum $name")
  }

  override def enterFunctionDeclaration(ctx: TypeScriptParser.FunctionDeclarationContext): Unit = {
    val name = Option(ctx.identifier()).map(_.getText).getOrElse("<anonymous>")
    _result += CodexEntry(relPath, SigType.FUNCTION, s"function $name")
  }

  override def enterClassDeclaration(ctx: TypeScriptParser.ClassDeclarationContext): Unit = {
    val name = Option(ctx.identifier()).map(_.getText).getOrElse("<anonymous>")
    _result += CodexEntry(relPath, SigType.CLASS, s"class $name")
  }

  override def enterVariableStatement(ctx: TypeScriptParser.VariableStatementContext): Unit = {
    val vdl = ctx.variableDeclarationList()
    val modifier = Option(vdl.varModifier()).map(_.getText).getOrElse("var")
    (0 until vdl.variableDeclaration().size()).foreach { i =>
      val vd = vdl.variableDeclaration(i)
      val name = Option(vd.identifier()).map(_.getText).getOrElse("<anonymous>")
      _result += CodexEntry(relPath, SigType.CONST, s"$modifier $name")
    }
  }

  override def enterDecorator(ctx: TypeScriptParser.DecoratorContext): Unit = {
    val decoratorText = ctx.getText
    _result += CodexEntry(relPath, SigType.DECORATOR, decoratorText)
  }
}
