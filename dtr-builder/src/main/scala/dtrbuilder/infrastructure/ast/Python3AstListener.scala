package dtrbuilder.infrastructure.ast

import dtrbuilder.domain.models.{CodexEntry, SigType}
import dtrbuilder.infrastructure.ast.antlr.{Python3Parser, Python3ParserBaseListener}
import org.antlr.v4.runtime.misc.Interval

import scala.collection.mutable.ArrayBuffer

/** ANTLR ParseTreeListener for Python3 that builds CodexEntry list.
  *
  * Extracts: IMPORT, FROM_IMPORT, CLASS, FUNCTION (including decorated and async).
  */
class Python3AstListener(relPath: String) extends Python3ParserBaseListener {

  private val _result = ArrayBuffer.empty[CodexEntry]

  def result: Seq[CodexEntry] = _result.toSeq
  def size: Int = _result.size

  /** Get the original source text for a parser rule context, including whitespace. */
  private def originalTextOf(ctx: org.antlr.v4.runtime.ParserRuleContext): String = {
    val startIdx = ctx.start.getStartIndex()
    val stopIdx = ctx.stop.getStopIndex()
    ctx.start.getInputStream().getText(Interval.of(startIdx, stopIdx))
  }

  override def enterFuncdef(ctx: Python3Parser.FuncdefContext): Unit = {
    val name = Option(ctx.name()).map(_.getText).getOrElse("<anonymous>")
    _result += CodexEntry(relPath, SigType.FUNCTION, s"def $name")
  }

  /** Handles 'async def' at the top level (inside async_stmt). */
  override def enterAsync_stmt(ctx: Python3Parser.Async_stmtContext): Unit = {
    val funcdef = ctx.funcdef()
    if (funcdef != null) {
      val name = Option(funcdef.name()).map(_.getText).getOrElse("<anonymous>")
      _result += CodexEntry(relPath, SigType.FUNCTION, s"async def $name")
    }
  }

  /** Handles 'async def' inside a decorator (async_funcdef). */
  override def enterAsync_funcdef(ctx: Python3Parser.Async_funcdefContext): Unit = {
    val name = Option(ctx.funcdef()).flatMap(f => Option(f.name()).map(_.getText)).getOrElse("<anonymous>")
    _result += CodexEntry(relPath, SigType.FUNCTION, s"async def $name")
  }

  override def enterClassdef(ctx: Python3Parser.ClassdefContext): Unit = {
    val name = Option(ctx.name()).map(_.getText).getOrElse("<anonymous>")
    _result += CodexEntry(relPath, SigType.CLASS, s"class $name")
  }

  override def enterImport_name(ctx: Python3Parser.Import_nameContext): Unit = {
    val fullText = originalTextOf(ctx)
    _result += CodexEntry(relPath, SigType.IMPORT, fullText)
  }

  override def enterImport_from(ctx: Python3Parser.Import_fromContext): Unit = {
    val fullText = originalTextOf(ctx)
    _result += CodexEntry(relPath, SigType.FROM_IMPORT, fullText)
  }
}
