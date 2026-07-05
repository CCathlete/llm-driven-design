package itrcompiler.application.services

import itrcompiler.application.ports.ContentRead
import itrcompiler.domain.models.{CU, CUBatch}
import java.nio.file.Path

/** Service: deserializes batch content from JSON or YAML files.
  *
  * Delegates actual I/O and parsing to the ContentRead port
  * (implemented by FileSystem in infrastructure).
  */
final class ContentDeserialize(contentRead: ContentRead) extends Service {

  /** Parse a JSON content file into a CUBatch. */
  def fromJson(path: Path): CUBatch =
    contentRead.readJson(path)

  /** Parse a YAML content file into a CUBatch. */
  def fromYaml(path: Path): CUBatch =
    contentRead.readYaml(path)
}
