package dtrbuilder.application

import dtrbuilder.domain.{CodexEntry, FileEntry}

/** Port: extracts code signatures (classes, defs, imports, etc.) from a file. */
trait SignatureExtractor {
  def extract(entry: FileEntry): Seq[CodexEntry]
}
