package itrcompiler.domain.models

import java.nio.file.Path

/** Encapsulates all CLI flags and input sources for a single compile invocation.
  *
  * Modes:
  *   - Single CU: rawContent + cuId
  *   - Batch JSON: jsonContent path
  *   - Batch YAML: yamlContent path
  */
final case class CompileCommand(
    compile: Boolean,
    dtr: Option[Path],
    outFolder: Path,
    rawContent: Option[String],
    cuId: Option[String],
    jsonContent: Option[Path],
    yamlContent: Option[Path],
    force: Boolean
) extends Model
