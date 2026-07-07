package itrcompiler.application.ports

import java.nio.file.Path

/** Port: loads DTR content from a file path.
  *
  * Implemented by infrastructure (FileSystem). The loaded content is
  * used by Compile to derive coordinate rules for CU frames.
  */
trait DTRLoad extends Port {
  def load(path: Path): String
}
