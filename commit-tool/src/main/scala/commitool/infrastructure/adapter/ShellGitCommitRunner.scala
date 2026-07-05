package commitool.infrastructure.adapter

import commitool.application.ports.GitCommitRunner
import commitool.domain.models.{CommitValidationError, GitError}
import java.io.{File, PrintWriter}
import scala.sys.process._
import scala.util.control.NonFatal

class ShellGitCommitRunner(workDir: Option[java.io.File] = None) extends GitCommitRunner {

  override def commit(message: String): Either[CommitValidationError, Unit] = {
    var tempFile: Option[File] = None
    try {
      tempFile = Some(File.createTempFile("commit_message_", ".tmp"))
      val writer = new PrintWriter(tempFile.get)
      try {
        writer.write(message)
      } finally {
        writer.close()
      }

      val stdout = new StringBuilder
      val stderr = new StringBuilder

      val processLogger = ProcessLogger(
        (o: String) => stdout.append(o).append("\n"),
        (e: String) => stderr.append(e).append("\n")
      )

      val command = s"git commit -F ${tempFile.get.getAbsolutePath}"
      val exitCode = command ! processLogger

      if (exitCode == 0) {
        Right(())
      } else {
        Left(GitError(stderr.toString.trim))
      }
    } catch {
      case NonFatal(e) => Left(GitError(s"Failed to create temp file or execute git command: ${e.getMessage}"))
    } finally {
      tempFile.foreach(_.delete())
    }
  }
}
