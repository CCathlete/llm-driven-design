package itrcompiler.infrastructure.filesystem

import itrcompiler.application.ports.{ContentRead, CUWrite, DTRLoad}
import itrcompiler.domain.models.{CU, CUBatch, RegularCU}
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
    * Layout: regular CUs live in a per-CU subdirectory `<outFolder>/<cu-id>/`.
    * A CU carrying components gets ONLY its four component files
    * (COORDINATES/REQUIREMENTS/IMPLEMENTATION_STEPS/ACCEPTANCE.itr) — no
    * per-CU `.itr` blob is ever written for it. A component-less CU keeps a
    * single `<cu-id>.itr` frame inside its folder (never at the output
    * root). ARCH.itr, LEGEND.itr and verification files stay at the root.
    * File name is determined by CU.fileName (e.g., ARCH.itr, LEGEND.itr).
    * If a target file already exists and force=false, it is skipped.
    */
  override def write(cu: CU, outFolder: Path, force: Boolean): Unit = {
    if (cu.cuType == RegularCU && cu.components.nonEmpty) {
      writeComponents(cu, outFolder, force)
      return
    }
    val cuDir =
      if (cu.cuType == RegularCU) outFolder.resolve(cu.id)
      else outFolder
    Files.createDirectories(cuDir)
    val cuFile = cuDir.resolve(cu.fileName)

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

  /** Write the four component files of a CU into its subdirectory.
    * No per-CU `.itr` blob is written. Same force semantics as frames.
    */
  private def writeComponents(cu: CU, outFolder: Path, force: Boolean): Unit = {
    val cuDir = outFolder.resolve(cu.id)
    Files.createDirectories(cuDir)
    cu.components.foreach { case (key, body) =>
      val target = cuDir.resolve(CU.componentFileNames.getOrElse(key, key.toUpperCase + ".itr"))
      if (!force && Files.exists(target)) ()
      else {
        val componentFrame = s"# ${key.toUpperCase.replace("-", "_")}: ${cu.id}\n\n$body\n"
        Files.write(target, componentFrame.getBytes(StandardCharsets.UTF_8))
      }
    }
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
