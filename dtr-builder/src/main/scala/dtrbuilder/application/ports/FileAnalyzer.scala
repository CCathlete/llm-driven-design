package dtrbuilder.application.ports

import dtrbuilder.domain.models.FileEntry

/** Port: enriches a FileEntry with MIME, encoding, language, byte size. */
trait FileAnalyzer {
  def analyze(entry: FileEntry): FileEntry
}
