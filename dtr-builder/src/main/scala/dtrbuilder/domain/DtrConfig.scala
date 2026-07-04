package dtrbuilder.domain

import java.nio.file.Path

/** Configuration for the DTR builder pipeline. */
final case class DtrConfig(
    pathRoot: Path,
    outputPath: Path,
    maxChunkSize: Long = DtrConfig.defaultMaxChunkSize,
    chunkEnabled: Boolean = true,
    noDotenv: Boolean = false,
    additionalFilters: Seq[String] = Seq.empty
)

object DtrConfig {
  val defaultMaxChunkSize: Long = 1024 * 1024 // 1 MB

  /** Create a DtrConfig with defaults, only requiring root and output paths. */
  def apply(pathRoot: Path, outputPath: Path): DtrConfig =
    DtrConfig(
      pathRoot = pathRoot,
      outputPath = outputPath,
      maxChunkSize = defaultMaxChunkSize,
      chunkEnabled = true,
      noDotenv = false,
      additionalFilters = Seq.empty
    )
}
