package dtrbuilder.infrastructure

import dtrbuilder.application.FileWalker
import dtrbuilder.domain.FileEntry
import java.nio.file.{Files, Path}
import java.util.stream.Stream
import scala.jdk.CollectionConverters._
import scala.util.Try

/** Infrastructure adapter: walks a file tree using java.nio.file.Files.walk.
  * Filters out directories and files matching the blocklist patterns.
  *
  * Default blocklist: .git, node_modules, target, build, dist, .next, .cache,
  * *.class, *.jar, *.exe, and other binary files.
  */
class FileSystemWalker(additionalFilters: Seq[String] = Seq.empty) extends FileWalker {

  import FileSystemWalker._

  /** Walk the file tree rooted at rootPath, yielding FileEntry for each file. */
  override def walk(rootPath: Path): LazyList[FileEntry] = {
    val normalizedRoot = rootPath.toAbsolutePath.normalize

    if (!Files.isDirectory(normalizedRoot)) {
      // Single file mode
      if (isAllowed(normalizedRoot)) {
        LazyList(fromPath(normalizedRoot, normalizedRoot.getParent))
      } else LazyList.empty
    } else {
      try {
        val stream: Stream[Path] = Files.walk(normalizedRoot)
        try {
          // Consume eagerly inside the try block before the stream closes
          val entries = stream.iterator().asScala
            .filter(p => !Files.isDirectory(p))
            .filter(isAllowed)
            .map(p => fromPath(p, normalizedRoot))
            .toVector
          LazyList(entries: _*)
        } finally {
          stream.close()
        }
      } catch {
        case e: Exception =>
          System.err.println(s"Error walking tree $normalizedRoot: ${e.getMessage}")
          LazyList.empty
      }
    }
  }

  /** Check if a path is allowed (not matching any blocklist pattern). */
  private def isAllowed(path: Path): Boolean = {
    val fileName = path.getFileName.toString
    val fullPath = path.toString.replace('\\', '/')

    !blocklist.exists { pattern =>
      if (pattern.startsWith("*.")) {
        // Extension pattern: *.class, *.jar, etc.
        fileName.endsWith(pattern.drop(1))
      } else if (pattern.endsWith("/")) {
        // Directory pattern: .git/, node_modules/, etc.
        fullPath.contains("/" + pattern)
      } else {
        // Exact filename or substring
        fileName == pattern || fullPath.contains("/" + pattern + "/")
      }
    }
  }

  /** Create a FileEntry from a Path relative to the root. */
  private def fromPath(path: Path, root: Path): FileEntry = {
    val relPath = root.relativize(path).toString.replace('\\', '/')
    val fileName = path.getFileName.toString
    val extIndex = fileName.lastIndexOf('.')
    val extension = if (extIndex > 0) fileName.substring(extIndex + 1) else ""
    val size = Try(Files.size(path)).getOrElse(0L)

    FileEntry(
      relPath = relPath,
      absolutePath = path.toAbsolutePath.normalize,
      byteSize = size,
      mimeType = "application/octet-stream", // placeholder, enriched by FileAnalyzer
      encoding = "UTF-8", // placeholder, enriched by FileAnalyzer
      language = None,
      extension = extension,
      isDirectory = false
    )
  }
}

object FileSystemWalker {
  val defaultBlocklist: Seq[String] = Seq(
    ".git/", "node_modules/", "target/", "build/", "dist/", ".next/", ".cache/",
    "*.class", "*.jar", "*.exe", "*.bin", "*.dll", "*.so", "*.dylib",
    "*.png", "*.jpg", "*.jpeg", "*.gif", "*.svg", "*.ico",
    "*.ttf", "*.otf", "*.woff", "*.woff2", "*.eot",
    "*.zip", "*.tar", "*.gz", "*.bz2", "*.7z", "*.rar",
    "*.mp3", "*.mp4", "*.avi", "*.mov", "*.wav", "*.flac",
    "*.pdf", "*.doc", "*.docx", "*.xls", "*.xlsx",
    "*.pyc", "*.pyo",
    ".DS_Store", "Thumbs.db"
  )

  private var _blocklist: Seq[String] = defaultBlocklist

  def blocklist: Seq[String] = _blocklist

  def addToBlocklist(patterns: Seq[String]): Unit = {
    _blocklist = _blocklist ++ patterns
  }

  def resetBlocklist(): Unit = {
    _blocklist = defaultBlocklist
  }
}
