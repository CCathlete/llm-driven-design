package dtrbuilder.infrastructure.metadata

import dtrbuilder.application.ports.FileAnalyzer
import dtrbuilder.domain.models.FileEntry
import java.nio.file.{Files, Path}
import scala.util.Try

class FileMetadataAnalyzer extends FileAnalyzer {

  override def analyze(entry: FileEntry): FileEntry = {
    val path = entry.absolutePath

    val mimeType = detectMimeType(path)
    val encoding = detectEncoding(path)
    val byteSize = Try(Files.size(path)).getOrElse(entry.byteSize)

    entry.copy(
      byteSize = byteSize,
      mimeType = mimeType,
      encoding = encoding
    )
  }

  private def detectMimeType(path: Path): String = {
    val probed = Try(Option(Files.probeContentType(path))).toOption.flatten
    probed.getOrElse {
      val name = path.getFileName.toString.toLowerCase
      if (name.endsWith(".scala")) "text/x-scala"
      else if (name.endsWith(".java")) "text/x-java"
      else if (name.endsWith(".py")) "text/x-python"
      else if (name.endsWith(".js") || name.endsWith(".jsx")) "text/javascript"
      else if (name.endsWith(".ts") || name.endsWith(".tsx")) "text/typescript"
      else if (name.endsWith(".md") || name.endsWith(".mdx")) "text/markdown"
      else if (name.endsWith(".json")) "application/json"
      else if (name.endsWith(".yaml") || name.endsWith(".yml")) "text/yaml"
      else if (name.endsWith(".xml") || name.endsWith(".html") || name.endsWith(".css")) "text/plain"
      else if (name.endsWith(".sh") || name.endsWith(".bash") || name.endsWith(".zsh")) "text/x-shellscript"
      else if (name.endsWith(".rs")) "text/x-rust"
      else if (name.endsWith(".go")) "text/x-go"
      else if (name.endsWith(".rb")) "text/x-ruby"
      else if (name.endsWith(".c") || name.endsWith(".h")) "text/x-c"
      else if (name.endsWith(".cpp") || name.endsWith(".hpp") || name.endsWith(".cc")) "text/x-c++"
      else if (name.endsWith(".toml")) "text/toml"
      else if (name.endsWith(".env") || name.endsWith(".env.*")) "text/plain"
      else "application/octet-stream"
    }
  }

  private def detectEncoding(path: Path): String = {
    try {
      val bytes = Files.readAllBytes(path)
      if (bytes.length < 2) return "UTF-8"

      if (bytes.length >= 3 && bytes(0) == 0xEF.toByte && bytes(1) == 0xBB.toByte && bytes(2) == 0xBF.toByte)
        return "UTF-8-BOM"
      if (bytes.length >= 2 && bytes(0) == 0xFE.toByte && bytes(1) == 0xFF.toByte)
        return "UTF-16BE"
      if (bytes.length >= 2 && bytes(0) == 0xFF.toByte && bytes(1) == 0xFE.toByte)
        return "UTF-16LE"

      val text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
      val roundtrip = text.getBytes(java.nio.charset.StandardCharsets.UTF_8)
      if (java.util.Arrays.equals(bytes, roundtrip)) "UTF-8"
      else "ISO-8859-1"
    } catch {
      case _: Exception => "UTF-8"
    }
  }
}
