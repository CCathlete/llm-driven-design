package dtrbuilder.application

import dtrbuilder.domain._

/** Application service: transforms domain objects into flat DTR KEY=VALUE lines.
  * Produces entries like:
  *   FILE.<relpath>=SIZE:<bytes>,MIME:<type>,ENCODING:<charset>,LANG:<lang>,EXT:<ext>
  *   CODEX.<relpath>=<SIGTYPE>:<signature>
  *   TYPE.<fqn>=KIND:<kind>,FILE:<relpath>
  *   REL.<fromFQN>-><toFQN>=<RELTYPE>:<detail>
  *   META.<key>=<value>
  */
class DtrFormatter {

  /** Transform all domain objects into an iterator of raw DTR lines. */
  def format(
      fileEntry: FileEntry,
      codexEntries: Seq[CodexEntry],
      typeDefs: Seq[TypeDef],
      relations: Seq[Relation],
      config: DtrConfig
  ): Iterator[RawEntry] = {
    val lines = Seq.newBuilder[RawEntry]

    // FILE entries
    val fileLine = fileEntryToLine(fileEntry)
    lines += fileLine

    // CODEX entries
    codexEntries.foreach { ce =>
      lines += RawEntry(s"CODEX.${ce.relPath}=${ce.sigType.entryName}:${escapeValue(ce.signatureText)}")
    }

    // TYPE entries
    typeDefs.foreach { td =>
      lines += RawEntry(s"TYPE.${td.fullyQualifiedName}=KIND:${td.kind},FILE:${td.sourceFile}")
    }

    // REL entries
    relations.foreach { r =>
      lines += RawEntry(s"REL.${r.fromFqn}->${r.toFqn}=${r.relationType.name}:${escapeValue(r.detail)}")
    }

    lines.result().iterator
  }

  /** Convert a FileEntry to DTR format. */
  private def fileEntryToLine(fe: FileEntry): RawEntry = {
    val lang = fe.language.map(_.name).getOrElse("Unknown")
    RawEntry(
      s"FILE.${fe.relPath}=SIZE:${fe.byteSize},MIME:${fe.mimeType},ENCODING:${fe.encoding},LANG:$lang,EXT:${fe.extension}"
    )
  }

  /** Escape values that contain special characters. */
  private def escapeValue(value: String): String = {
    if (value.contains(",") || value.contains("=") || value.contains(":") || value.contains("\n")) {
      // Simple escaping: replace problematic characters
      value.replace("\\", "\\\\").replace(",", "\\,").replace("=", "\\=").replace("\n", "\\n")
    } else value
  }
}
