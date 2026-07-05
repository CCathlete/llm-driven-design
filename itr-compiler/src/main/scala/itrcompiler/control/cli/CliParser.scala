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

    val flags = args.sliding(2, 1).collect {
      case Array("--compile", _)      => "--compile" -> ""
      case Array("--dtr", v)          => "--dtr" -> v
      case Array("--out-folder", v)   => "--out-folder" -> v
      case Array("--raw-content", v)  => "--raw-content" -> v
      case Array("--cu-id", v)        => "--cu-id" -> v
      case Array("--json-content", v) => "--json-content" -> v
      case Array("--yaml-content", v) => "--yaml-content" -> v
      case Array("--force", _)        => "--force" -> ""
    }.toMap

    Some(CompileCommand(
      compile      = flags.contains("--compile"),
      dtr          = flags.get("--dtr").map(Paths.get(_)),
      outFolder    = flags.get("--out-folder").map(Paths.get(_)).getOrElse(Paths.get("out")),
      rawContent   = flags.get("--raw-content"),
      cuId         = flags.get("--cu-id"),
      jsonContent  = flags.get("--json-content").map(Paths.get(_)),
      yamlContent  = flags.get("--yaml-content").map(Paths.get(_)),
      force        = flags.contains("--force")
    ))
  }
}
