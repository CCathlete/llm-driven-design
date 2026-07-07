package itrcompiler.application.ports

import itrcompiler.domain.models.CUBatch
import java.nio.file.Path

/** Port: reads and deserializes content from JSON or YAML input files.
  *
  * JSON structure:  [{cu-id, dtr-coordinates:[str], content:str}]
  * YAML structure:  cu-id: {dtr-coordinates:[str], content:str}
  */
trait ContentRead extends Port {
  def readJson(path: Path): CUBatch
  def readYaml(path: Path): CUBatch
}
