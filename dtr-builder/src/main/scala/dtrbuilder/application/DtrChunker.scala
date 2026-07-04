package dtrbuilder.application

import dtrbuilder.domain.{DtrChunk, RawEntry}

/** Application service: accumulates raw DTR lines and splits at file boundaries
  * when approaching DTR_MAX_CHUNK_SIZE.
  */
class DtrChunker {

  /** Split raw entries into chunks, each respecting maxChunkBytes.
    * Splits occur at file boundaries (FILE. prefix) to avoid breaking file metadata
    * across chunks. The last chunk may be smaller than maxChunkBytes.
    */
  def chunk(entries: Iterator[RawEntry], maxChunkBytes: Long): Seq[DtrChunk] = {
    val chunks = Seq.newBuilder[DtrChunk]
    var currentEntries = Vector.empty[RawEntry]
    var currentSize = 0L
    var chunkIndex = 0

    def flushCurrent(): Unit = {
      if (currentEntries.nonEmpty) {
        chunks += DtrChunk(
          entries = currentEntries,
          byteSize = currentSize,
          chunkIndex = chunkIndex,
          totalChunks = 0 // placeholder, updated after all chunks known
        )
        chunkIndex += 1
        currentEntries = Vector.empty
        currentSize = 0L
      }
    }

    while (entries.hasNext) {
      val entry = entries.next()
      val entrySize = entry.tensorLine.getBytes("UTF-8").length.toLong + 1L // +1 for newline

      // If this is a FILE line (start of a new file section) and adding it would exceed max,
      // flush the current chunk and start a new one
      if (entry.tensorLine.startsWith("FILE.") && currentSize + entrySize > maxChunkBytes && currentEntries.nonEmpty) {
        flushCurrent()
      }

      currentEntries = currentEntries :+ entry
      currentSize += entrySize

      // If we exceed max chunk size (not at a file boundary), flush anyway
      if (currentSize >= maxChunkBytes) {
        flushCurrent()
      }
    }

    // Flush remaining entries
    if (currentEntries.nonEmpty) {
      chunks += DtrChunk(
        entries = currentEntries,
        byteSize = currentSize,
        chunkIndex = chunkIndex,
        totalChunks = 0
      )
      chunkIndex += 1
    }

    val allChunks = chunks.result()
    // Update totalChunks for all chunks
    val total = allChunks.size
    allChunks.map(c => c.copy(totalChunks = total))
  }
}
