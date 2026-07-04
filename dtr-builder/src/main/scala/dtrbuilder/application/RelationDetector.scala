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
        fromFqn = file.replace('/', '.'),
        toFqn = target,
        relationType = RelationType.IMPORT_DEP,
        detail = s"$file imports $target"
      )
    }
  }

  /** Detect EXTENDS/IMPLEMENTS relations from EXTENDS/IMPLEMENTS sigs or CLASS/TRAIT sig text. */
  private def detectExtends(entries: Seq[CodexEntry]): Seq[Relation] = {
    entries.flatMap { entry =>
      entry.sigType match {
        // Explicit EXTENDS entries produced by extractors (e.g. "FileSystemDtrWriter extends DtrWriter")
        case SigType.EXTENDS =>
          entry.signatureText.split(" extends ", 2) match {
            case Array(child, parent) =>
              val childName = child.trim
              val parentName = parent.trim.split("[\\[\\s]")(0) // handle "Trait[TypeParam]"
              Some(Relation(
                fromFqn = s"${entry.relPath.replace('/', '.')}.$childName",
                toFqn = parentName,
                relationType = RelationType.EXTENDS,
                detail = entry.signatureText
              ))
            case _ => None
          }
        // Explicit IMPLEMENTS entries produced by extractors (e.g. "TupleSink implements Consumer")
        case SigType.IMPLEMENTS =>
          entry.signatureText.split(" implements ", 2) match {
            case Array(child, parent) =>
              val childName = child.trim
              val parentName = parent.trim.split("[\\[\\s]")(0)
              Some(Relation(
                fromFqn = s"${entry.relPath.replace('/', '.')}.$childName",
                toFqn = parentName,
                relationType = RelationType.IMPLEMENTS,
                detail = entry.signatureText
              ))
            case _ => None
          }
        // Fallback: parse "extends"/"implements" from CLASS/TRAIT signature text (for extractors
        // that embed the full declaration, e.g. "class A extends B")
        case _ =>
          val text = entry.signatureText
          val typeNamePattern = """(?:class|trait|object|interface|enum)\s+(\w+)""".r
          val typeName = typeNamePattern.findFirstMatchIn(text).map(_.group(1)).getOrElse(entry.sigType.entryName)
          val baseFqn = s"${entry.relPath.replace('/', '.')}.$typeName"
          val extendRelations = """extends\s+(\w+)""".r.findAllMatchIn(text).map { m =>
            Relation(
              fromFqn = baseFqn,
              toFqn = m.group(1),
              relationType = RelationType.EXTENDS,
              detail = m.group(0)
            )
          }
          val implementRelations = """implements\s+(\w+)""".r.findAllMatchIn(text).map { m =>
            Relation(
              fromFqn = baseFqn,
              toFqn = m.group(1),
              relationType = RelationType.IMPLEMENTS,
              detail = m.group(0)
            )
          }
          extendRelations ++ implementRelations
      }
    }
  }

  /** Detect HOLDS relations — type definitions that contain other types.
    * A file with multiple types may have one type holding another (e.g. an outer class and inner class).
    * Companion objects (same name, different sig kind) are skipped — they are peers, not nested.
    */
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
        typeSigs.tail.collect {
          case contained if contained.signatureText != container.signatureText =>
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
