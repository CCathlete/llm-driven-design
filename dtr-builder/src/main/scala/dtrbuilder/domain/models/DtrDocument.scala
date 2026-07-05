package dtrbuilder.domain.models

import java.nio.file.Path

/** The full DTR document, possibly split into chunks. */
final case class DtrDocument(
    root: Path,
    chunks: Seq[DtrChunk],
    fileEntries: Seq[FileEntry],
    codexEntries: Seq[CodexEntry],
    typeEntries: Seq[TypeDef],
    relationEntries: Seq[Relation],
    meta: Map[String, String]
) {
  def fileCount: Int   = fileEntries.size
  def codexCount: Int  = codexEntries.size
  def typeCount: Int   = typeEntries.size
  def relationCount: Int = relationEntries.size
}
