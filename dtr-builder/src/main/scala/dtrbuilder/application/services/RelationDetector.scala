package dtrbuilder.application.services

import dtrbuilder.domain.models.{CodexEntry, Relation, RelationType, SigType, TypeDef}

class RelationDetector {

  def detect(codexEntries: Seq[CodexEntry], typeDefs: Seq[TypeDef]): Seq[Relation] = {
    val importRelations = detectImports(codexEntries)
    val extendRelations = detectExtends(codexEntries)
    val holdRelations   = detectHolds(codexEntries, typeDefs)
    importRelations ++ extendRelations ++ holdRelations
  }

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

  private def detectExtends(entries: Seq[CodexEntry]): Seq[Relation] = {
    entries.flatMap { entry =>
      entry.sigType match {
        case SigType.EXTENDS =>
          entry.signatureText.split(" extends ", 2) match {
            case Array(child, parent) =>
              val childName = child.trim
              val parentName = parent.trim.split("[\\[\\s]")(0)
              Some(Relation(
                fromFqn = s"${entry.relPath.replace('/', '.')}.$childName",
                toFqn = parentName,
                relationType = RelationType.EXTENDS,
                detail = entry.signatureText
              ))
            case _ => None
          }
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
