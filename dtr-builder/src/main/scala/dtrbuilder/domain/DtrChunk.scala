package dtrbuilder.domain

/** A single chunk of DTR output, split when size exceeds the max. */
final case class DtrChunk(
    entries: Seq[RawEntry],
    byteSize: Long,
    chunkIndex: Int,
    totalChunks: Int
)
