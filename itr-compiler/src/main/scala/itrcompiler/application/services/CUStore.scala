package itrcompiler.application.services

import itrcompiler.application.ports.CUWrite
import itrcompiler.domain.models.CU
import java.nio.file.Path

/** Service: stores a CU frame to the output folder.
  *
  * Delegates file I/O to the CUWrite port.
  * The `force` flag controls overwrite behavior:
  *   true  → overwrite existing file
  *   false → skip if file exists
  */
final class CUStore(cuWrite: CUWrite) extends Service {

  /** Store a single CU frame. Returns the path it was written to. */
  def store(cu: CU, outFolder: Path, force: Boolean): Unit =
    cuWrite.write(cu, outFolder, force)
}
