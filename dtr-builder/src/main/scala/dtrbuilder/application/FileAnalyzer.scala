package dtrbuilder.application

import dtrbuilder.domain.FileEntry

/** Port: enriches a FileEntry with MIME, encoding, language, byte size. */
trait FileAnalyzer {
  def analyze(entry: FileEntry): FileEntry
}
