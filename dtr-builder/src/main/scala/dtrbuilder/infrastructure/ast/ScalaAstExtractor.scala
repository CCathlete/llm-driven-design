package dtrbuilder.infrastructure.ast

import dtrbuilder.application.SignatureExtractor
import dtrbuilder.domain.{CodexEntry, FileEntry, SigType}
import dtrbuilder.infrastructure.RegexSignatureExtractor
import scala.meta._

/** Full AST extractor for Scala using scala.meta.
  *
  * Parses source with scala.meta, traverses the tree, and emits CodexEntry
  * for: PACKAGE, IMPORT, CLASS, TRAIT, OBJECT, CASE_CLASS, CASE_OBJECT,
  * DEF, VAL, VAR, TYPE, CTOR, ENUM.
  *
  * Falls back to regex extraction only on parse failure.
  */
class ScalaAstExtractor(regexFallback: RegexSignatureExtractor) extends SignatureExtractor {

  override def extract(entry: FileEntry): Seq[CodexEntry] = {
    try {
      val input = Input.File(entry.absolutePath)
      val source = input.parse[Source] match {
        case parsers.Parsed.Success(tree) => tree
        case _ => return regexFallback.extract(entry)
      }
      val traverser = new ScalaTraverser(entry.relPath)
      traverser(source)
      val entries = traverser.buildResult()
      if (entries.isEmpty) regexFallback.extract(entry) else entries
    } catch {
      case _: Exception => regexFallback.extract(entry)
    }
  }

  private class ScalaTraverser(relPath: String) extends Traverser {
    private val buf = Seq.newBuilder[CodexEntry]

    def buildResult(): Seq[CodexEntry] = buf.result()

    override def apply(tree: Tree): Unit = tree match {
      case pkg: Pkg =>
        buf += CodexEntry(relPath, SigType.PACKAGE, pkg.name.toString)
        super.apply(pkg)

      case imp: Import =>
        imp.importers.foreach { importer =>
          importer.importees.foreach { importee =>
            val ref = importer.ref.toString
            importee match {
              case i: Importee.Name =>
                buf += CodexEntry(relPath, SigType.IMPORT, s"$ref.${i.name.toString}")
              case i: Importee.Rename =>
                buf += CodexEntry(relPath, SigType.IMPORT, s"$ref.{${i.name.toString} => ${i.rename.toString}}")
              case i: Importee.Wildcard =>
                buf += CodexEntry(relPath, SigType.IMPORT, s"$ref._")
              case _ => ()
            }
          }
        }
        super.apply(tree)

      case cls: Defn.Class =>
        val sigType = if (cls.mods.exists(_.is[Mod.Case])) SigType.CASE_CLASS else SigType.CLASS
        val name = cls.name.toString
        buf += CodexEntry(relPath, sigType, name)
        cls.templ.inits.foreach { init =>
          buf += CodexEntry(relPath, SigType.EXTENDS, s"$name extends ${init.toString}")
        }
        super.apply(tree)

      case trt: Defn.Trait =>
        val name = trt.name.toString
        buf += CodexEntry(relPath, SigType.TRAIT, name)
        trt.templ.inits.foreach { init =>
          buf += CodexEntry(relPath, SigType.EXTENDS, s"$name extends ${init.toString}")
        }
        super.apply(tree)

      case obj: Defn.Object =>
        val sigType = if (obj.mods.exists(_.is[Mod.Case])) SigType.CASE_OBJECT else SigType.OBJECT
        val name = obj.name.toString
        buf += CodexEntry(relPath, sigType, name)
        obj.templ.inits.foreach { init =>
          buf += CodexEntry(relPath, SigType.EXTENDS, s"$name extends ${init.toString}")
        }
        super.apply(tree)

      case enm: Defn.Enum =>
        val name = enm.name.toString
        buf += CodexEntry(relPath, SigType.ENUM, name)
        enm.templ.inits.foreach { init =>
          buf += CodexEntry(relPath, SigType.EXTENDS, s"$name extends ${init.toString}")
        }
        super.apply(tree)

      case defn: Defn.Def =>
        buf += CodexEntry(relPath, SigType.DEF, defn.name.toString)
        super.apply(tree)

      case valDef: Defn.Val =>
        valDef.pats.foreach { pat =>
          buf += CodexEntry(relPath, SigType.VAL, pat.toString)
        }
        super.apply(tree)

      case varDef: Defn.Var =>
        varDef.pats.foreach { pat =>
          buf += CodexEntry(relPath, SigType.VAR, pat.toString)
        }
        super.apply(tree)

      case typeDef: Defn.Type =>
        buf += CodexEntry(relPath, SigType.TYPE, typeDef.name.toString)
        super.apply(tree)

      case ctor: Ctor.Primary =>
        if (ctor.paramss.nonEmpty && ctor.paramss.flatten.nonEmpty) {
          buf += CodexEntry(relPath, SigType.CTOR, ctor.name.toString)
        }
        super.apply(tree)

      case enumCase: Defn.EnumCase =>
        buf += CodexEntry(relPath, SigType.CASE_CLASS, enumCase.name.toString)
        super.apply(tree)

      case _ => super.apply(tree)
    }
  }
}
