package dtrbuilder.application.ports

import dtrbuilder.domain.models.FileEntry
import java.nio.file.Path

/** Port: walks a file tree and yields FileEntry for every node. */
trait FileWalker {
  def walk(rootPath: Path): LazyList[FileEntry]
}
