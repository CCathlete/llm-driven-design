package itrcompiler.control.cli

import itrcompiler.domain.models.CompileCommand
import java.nio.file.Paths

/** CLI argument parser for the itr-compiler.
  *
  * Recognised flags:
  *   --compile                   Enable compile mode
  *   --dtr <path>                Path to DTR file (optional)
  *   --out-folder <path>         Output folder (default: "out")
  *   --raw-content <string>      Single CU content (requires --cu-id)
  *   --cu-id <id>                CU identifier for single mode
  *   --json-content <path>       Path to JSON batch file
  *   --yaml-content <path>       Path to YAML batch file
  *   --force                     Overwrite existing CU files
  *
  * Semantics:
  *   single: --raw-content + --cu-id → one CU frame
  *   json:   --json-content          → batch of frames
  *   yaml:   --yaml-content          → batch of frames
  */
object CliParser {

  /** Parse CLI arguments into an optional CompileCommand. */
  def parse(args: Array[String]): Option[CompileCommand] = {
    if (args.isEmpty) return None

    val flags = args.iterator
    val buf = Map.newBuilder[String, String]
    val bools = Set.newBuilder[String]

    while (flags.hasNext) {
      val flag = flags.next()
      flag match {
        case "--compile" | "--force" =>
          bools += flag
        case "--dtr" | "--out-folder" | "--raw-content" | "--cu-id" | "--json-content" | "--yaml-content" =>
          if (flags.hasNext) buf += flag -> flags.next()
        case _ =>
          // skip unknown flags
      }
    }

    val vals = buf.result()

    Some(CompileCommand(
      compile      = bools.result().contains("--compile"),
      dtr          = vals.get("--dtr").map(Paths.get(_)),
      outFolder    = vals.get("--out-folder").map(Paths.get(_)).getOrElse(Paths.get("out")),
      rawContent   = vals.get("--raw-content"),
      cuId         = vals.get("--cu-id"),
      jsonContent  = vals.get("--json-content").map(Paths.get(_)),
      yamlContent  = vals.get("--yaml-content").map(Paths.get(_)),
      force        = bools.result().contains("--force")
    ))
  }
}
