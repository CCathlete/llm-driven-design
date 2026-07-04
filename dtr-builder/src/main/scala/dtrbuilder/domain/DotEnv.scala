package dtrbuilder.domain

import java.nio.file.Path

/** Parsed .env key-value pairs with all ${} expansions resolved. */
final case class DotEnv(
    vars: Map[String, String],
    sourcePath: Option[Path]
) {
  /** Merge with system env vars. System takes precedence. */
  def mergedWithSystem(systemEnv: Map[String, String]): DotEnv =
    copy(vars = vars ++ systemEnv)

  def get(key: String): Option[String] = vars.get(key)
}
