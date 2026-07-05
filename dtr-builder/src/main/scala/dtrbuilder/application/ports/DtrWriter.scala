package dtrbuilder.application.ports

import dtrbuilder.domain.models.DtrChunk
import java.nio.file.Path

/** Port: writes DTR chunks to disk with -NNN suffix pattern.
  *
  * Output pattern: path/to/base-001.ext, path/to/base-002.ext
  * If no extension: basename-NNN
  * If chunking is disabled (single chunk), writes the base path as-is.
  */
trait DtrWriter {
  def write(chunks: Seq[DtrChunk], outputPath: Path): Seq[Path]
}
