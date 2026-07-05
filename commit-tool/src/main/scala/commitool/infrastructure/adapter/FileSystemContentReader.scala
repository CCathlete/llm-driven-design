package commitool.infrastructure.adapter

import commitool.application.ports.FileContentReader
import commitool.domain.models.{CommitValidationError, FileNotFound, MessageFileEmpty, ReadError}
import scala.io.{BufferedSource, Source}
import java.io.{FileNotFoundException, IOException}
import scala.util.control.NonFatal

class FileSystemContentReader extends FileContentReader {

  override def read(path: String): Either[CommitValidationError, String] = {
    var source: Option[BufferedSource] = None
    try {
      source = Some(Source.fromFile(path))
      val content = source.get.mkString
      if (content.trim.isEmpty) {
        Left(MessageFileEmpty(path))
      } else {
        Right(content)
      }
    } catch {
      case _: FileNotFoundException => Left(FileNotFound(path))
      case e: IOException => Left(ReadError(path, e.getMessage))
      case NonFatal(e) => Left(ReadError(path, e.getMessage))
    } finally {
      source.foreach(_.close())
    }
  }
}
