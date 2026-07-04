package dtrbuilder.application

import dtrbuilder.domain.{CodexEntry, Relation, RelationType, SigType, TypeDef}

/** Application service: infers dependency edges from codex entries and type definitions.
  * Walks imports, extends, and uses patterns to produce REL entries.
  */
class RelationDetector {

  /** Detect all relations from a set of codex entries and type definitions. */
  def detect(codexEntries: Seq[CodexEntry], typeDefs: Seq[TypeDef]): Seq[Relation] = {
    val importRelations = detectImports(codexEntries)
    val extendRelations = detectExtends(codexEntries)
    val holdRelations   = detectHolds(codexEntries, typeDefs)
    importRelations ++ extendRelations ++ holdRelations
  }

  /** Detect IMPORT_DEP relations from PACKAGE/IMPORT/FROM_IMPORT/USE/REQUIRE sigs. */
  private def detectImports(entries: Seq[CodexEntry]): Seq[Relation] = {
    entries.collect {
      case CodexEntry(file, SigType.IMPORT, text) =>
        file -> text
      case CodexEntry(file, SigType.FROM_IMPORT, text) =>
        file -> text
      case CodexEntry(file, SigType.USE, text) =>
        file -> text
      case CodexEntry(file, SigType.REQUIRE, text) =>
        file -> text
      case CodexEntry(file, SigType.INCLUDE, text) =>
        file -> text
    }.map { case (file, target) =>
      Relation(
        fromFqn = file.replace('/', '.').replaceAll("\\.\\w+$", ""),
        toFqn = target,
        relationType = RelationType.IMPORT_DEP,
        detail = s"$file imports $target"
      )
    }
  }

  /** Detect EXTENDS/IMPLEMENTS relations from CLASS/TRAIT/INTERFACE sigs. */
  private def detectExtends(entries: Seq[CodexEntry]): Seq[Relation] = {
    entries.flatMap { entry =>
      val text = entry.signatureText
      val extendRelations = """extends\s+(\w+)""".r.findAllMatchIn(text).map { m =>
        Relation(
          fromFqn = s"${entry.relPath.replace('/', '.')}.${entry.sigType.entryName}",
          toFqn = m.group(1),
          relationType = RelationType.EXTENDS,
          detail = m.group(0)
        )
      }
      val implementRelations = """implements\s+(\w+)""".r.findAllMatchIn(text).map { m =>
        Relation(
          fromFqn = s"${entry.relPath.replace('/', '.')}.${entry.sigType.entryName}",
          toFqn = m.group(1),
          relationType = RelationType.IMPLEMENTS,
          detail = m.group(0)
        )
      }
      extendRelations ++ implementRelations
    }
  }

  /** Detect HOLDS relations — type definitions that contain other types. */
  private def detectHolds(entries: Seq[CodexEntry], typeDefs: Seq[TypeDef]): Seq[Relation] = {
    val entriesByFile = entries.groupBy(_.relPath)
    entriesByFile.flatMap { case (file, fileEntries) =>
      val typeSigs = fileEntries.filter(e =>
        e.sigType == SigType.CLASS || e.sigType == SigType.TRAIT ||
        e.sigType == SigType.OBJECT || e.sigType == SigType.INTERFACE ||
        e.sigType == SigType.ENUM || e.sigType == SigType.STRUCT
      )
      if (typeSigs.size > 1) {
        val container = typeSigs.head
        typeSigs.tail.map { contained =>
          Relation(
            fromFqn = s"${file.replace('/', '.')}.${container.signatureText}",
            toFqn = s"${file.replace('/', '.')}.${contained.signatureText}",
            relationType = RelationType.HOLDS,
            detail = s"${container.signatureText} holds ${contained.signatureText}"
          )
        }
      } else Seq.empty
    }.toSeq
  }
}
