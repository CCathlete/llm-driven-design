package dtrbuilder.infrastructure.writer

import dtrbuilder.application.ports.DtrWriter
import dtrbuilder.domain.models.DtrChunk
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

class FileSystemDtrWriter extends DtrWriter {

  override def write(chunks: Seq[DtrChunk], outputPath: Path): Seq[Path] = {
    if (chunks.isEmpty) return Seq.empty

    if (chunks.size == 1) {
      writeChunk(chunks.head, outputPath)
      Seq(outputPath)
    } else {
      chunks.map { chunk =>
        val chunkPath = chunkPathFor(outputPath, chunk.chunkIndex + 1, chunks.size)
        writeChunk(chunk, chunkPath)
        chunkPath
      }
    }
  }

  private def chunkPathFor(base: Path, index: Int, total: Int): Path = {
    val parent = base.getParent
    val filename = base.getFileName.toString
    val dotIndex = filename.lastIndexOf('.')
    val (baseName, ext) =
      if (dotIndex > 0) (filename.substring(0, dotIndex), filename.substring(dotIndex))
      else (filename, "")

    val paddedIndex = f"$index%03d"
    val chunkFilename = if (ext.nonEmpty) s"$baseName-$paddedIndex$ext" else s"$baseName-$paddedIndex"

    if (parent == null) Paths.get(chunkFilename)
    else parent.resolve(chunkFilename)
  }

  private def writeChunk(chunk: DtrChunk, path: Path): Unit = {
    if (path.getParent != null) {
      Files.createDirectories(path.getParent)
    }
    val lines = chunk.entries.map(_.tensorLine)
    val content = lines.mkString("", "\n", "\n")
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
  }
}
