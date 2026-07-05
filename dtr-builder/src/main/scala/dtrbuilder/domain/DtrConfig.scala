package dtrbuilder.domain

import java.nio.file.Path

/** Configuration for the DTR builder pipeline. */
final case class DtrConfig(
    pathRoot: Path,
    outputPath: Path,
    maxChunkSize: Long = DtrConfig.defaultMaxChunkSize,
    chunkEnabled: Boolean = true,
    noDotenv: Boolean = false,
    additionalFilters: Seq[String] = Seq.empty,
    mode: DtrConfig.Mode = DtrConfig.ExtractMode
)

object DtrConfig {
  val defaultMaxChunkSize: Long = 1024 * 1024 // 1 MB

  /** Sealed trait for distinguishing pipeline modes. */
  sealed trait Mode
  case object ExtractMode extends Mode
  final case class CreateBaselineMode(
      appName: String,
      packageName: String,
      language: String
  ) extends Mode

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
