package dtrbuilder.domain.models

import java.nio.file.Path

/** Result of a DTR build operation. */
final case class BuildResult(
    outputPaths: Seq[Path],
    fileCount: Int,
    codexCount: Int,
    typeCount: Int,
    relationCount: Int,
    chunkCount: Int,
    dotEnv: DotEnv
)
