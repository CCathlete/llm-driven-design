package itrcompiler.application.ports

import itrcompiler.domain.models.CU
import java.nio.file.Path

/** Port: writes a single CU frame to the output folder.
  *
  * Frame structure:
  *   header: CU-ID + timestamp + DTR coordinates
  *   body:   free-form content (passed through unchanged)
  *
  * Override behavior: if force=true overwrite existing, else skip.
  */
trait CUWrite extends Port {
  def write(cu: CU, outFolder: Path, force: Boolean): Unit
}
