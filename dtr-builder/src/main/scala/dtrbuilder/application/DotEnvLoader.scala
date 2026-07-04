package dtrbuilder.application

import dtrbuilder.domain.DotEnv
import java.nio.file.Path

/** Port: discovers, parses, and loads .env files with ${} expansion. */
trait DotEnvLoader {
  def load(rootPath: Path): DotEnv
}
