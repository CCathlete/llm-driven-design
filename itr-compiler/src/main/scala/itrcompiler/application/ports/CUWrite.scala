package itrcompiler.application.ports

import itrcompiler.domain.models.{CU, CUSynopsis}
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

  /** Write the root SYNOPSIS.itr once per compile.
    * Default no-op keeps legacy test doubles compiling; the real
    * FileSystem adapter overrides with file output.
    */
  def writeSynopsis(outFolder: Path, synopsis: CUSynopsis, cus: Seq[CU], force: Boolean): Unit = ()
}
