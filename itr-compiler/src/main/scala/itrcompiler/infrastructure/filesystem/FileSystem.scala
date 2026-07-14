package itrcompiler.infrastructure.filesystem

import itrcompiler.application.ports.{ContentRead, CUWrite, DTRLoad}
import itrcompiler.domain.models.{CU, CUBatch}
import itrcompiler.infrastructure.parsers.{JSONFormat, YAMLFormat}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

/** Infrastructure adapter: provides file system access implementing all
  * three I/O ports (DTRLoad, CUWrite, ContentRead).
  *
  * This is the concrete adapter that bridges the application core
  * (ports) with the file system. It uses JSONFormat and YAMLFormat
  * internally for content deserialization.
  */
final class FileSystem(
    jsonFormat: JSONFormat = new JSONFormat,
    yamlFormat: YAMLFormat = new YAMLFormat
) extends DTRLoad with CUWrite with ContentRead {

  // ── DTRLoad ────────────────────────────────────────────────

  /** Load the full content of a DTR file as a UTF-8 string. */
  override def load(path: Path): String =
    new String(Files.readAllBytes(path), StandardCharsets.UTF_8)

  // ── CUWrite ────────────────────────────────────────────────

  /** Write a single CU frame to the output folder.
    *
    * Frame format:
    *   # CU-ID: <id>
    *   # CU-TYPE: <type>
    *   # TIMESTAMP: <timestamp>
    *   # DTR-COORDINATES: <coord1>, <coord2>, ...
    *   <blank line>
    *   <content>
    *
    * File name is determined by CU.fileName (e.g., ARCH.itr, LEGEND.itr).
    * If the file already exists and force=false, it is skipped.
    */
  override def write(cu: CU, outFolder: Path, force: Boolean): Unit = {
    Files.createDirectories(outFolder)
    val cuFile = outFolder.resolve(cu.fileName)

    if (!force && Files.exists(cuFile)) return

    val header = Seq(
      s"# CU-ID: ${cu.id}",
      s"# CU-TYPE: ${cu.cuType}",
      s"# TIMESTAMP: ${cu.timestamp}",
      s"# DTR-COORDINATES: ${cu.dtrCoordinates.mkString(", ")}"
    ).mkString("\n")

    val frame = s"$header\n\n${cu.content}\n"
    Files.write(cuFile, frame.getBytes(StandardCharsets.UTF_8))
  }

  // ── ContentRead ────────────────────────────────────────────

  /** Read and parse a JSON content file into a CUBatch. */
  override def readJson(path: Path): CUBatch = {
    val content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
    jsonFormat.parse(content)
  }

  /** Read and parse a YAML content file into a CUBatch. */
  override def readYaml(path: Path): CUBatch = {
    val content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
    yamlFormat.parse(content)
  }
}
