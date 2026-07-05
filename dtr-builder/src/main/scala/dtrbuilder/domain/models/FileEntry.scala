package dtrbuilder.domain.models

import java.nio.file.Path

/** Represents a single file discovered during tree walk. */
final case class FileEntry(
    relPath: String,
    absolutePath: Path,
    byteSize: Long,
    mimeType: String,
    encoding: String,
    language: Option[Language],
    extension: String,
    isDirectory: Boolean
)
