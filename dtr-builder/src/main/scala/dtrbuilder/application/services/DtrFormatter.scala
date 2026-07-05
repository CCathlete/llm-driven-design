package dtrbuilder.application.services

import dtrbuilder.domain.models._
import dtrbuilder.domain.models.DtrConfig

class DtrFormatter {

  def format(
      fileEntry: FileEntry,
      codexEntries: Seq[CodexEntry],
      typeDefs: Seq[TypeDef],
      relations: Seq[Relation],
      config: DtrConfig
  ): Iterator[RawEntry] = {
    val lines = Seq.newBuilder[RawEntry]

    val fileLine = fileEntryToLine(fileEntry)
    lines += fileLine

    codexEntries.foreach { ce =>
      lines += RawEntry(s"CODEX.${ce.relPath}=${ce.sigType.entryName}:${escapeValue(ce.signatureText)}")
    }

    typeDefs.foreach { td =>
      lines += RawEntry(s"TYPE.${td.fullyQualifiedName}=KIND:${td.kind},FILE:${td.sourceFile}")
    }

    relations.foreach { r =>
      lines += RawEntry(s"REL.${r.fromFqn}->${r.toFqn}=${r.relationType.name}:${escapeValue(r.detail)}")
    }

    lines.result().iterator
  }

  private def fileEntryToLine(fe: FileEntry): RawEntry = {
    val lang = fe.language.map(_.name).getOrElse("Unknown")
    RawEntry(
      s"FILE.${fe.relPath}=SIZE:${fe.byteSize},MIME:${fe.mimeType},ENCODING:${fe.encoding},LANG:$lang,EXT:${fe.extension}"
    )
  }

  private def escapeValue(value: String): String = {
    if (value.contains(",") || value.contains("=") || value.contains(":") || value.contains("\n")) {
      value.replace("\\", "\\\\").replace(",", "\\,").replace("=", "\\=").replace("\n", "\\n")
    } else value
  }
}
