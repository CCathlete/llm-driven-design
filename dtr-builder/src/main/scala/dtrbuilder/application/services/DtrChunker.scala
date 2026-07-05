package dtrbuilder.application.services

import dtrbuilder.domain.models.{DtrChunk, RawEntry}

class DtrChunker {

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
          totalChunks = 0
        )
        chunkIndex += 1
        currentEntries = Vector.empty
        currentSize = 0L
      }
    }

    while (entries.hasNext) {
      val entry = entries.next()
      val entrySize = entry.tensorLine.getBytes("UTF-8").length.toLong + 1L

      if (entry.tensorLine.startsWith("FILE.") && currentSize + entrySize > maxChunkBytes && currentEntries.nonEmpty) {
        flushCurrent()
      }

      currentEntries = currentEntries :+ entry
      currentSize += entrySize

      if (currentSize >= maxChunkBytes) {
        flushCurrent()
      }
    }

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
    val total = allChunks.size
    allChunks.map(c => c.copy(totalChunks = total))
  }
}
