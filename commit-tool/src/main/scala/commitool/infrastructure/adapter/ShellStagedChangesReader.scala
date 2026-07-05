package commitool.infrastructure.adapter

import commitool.application.ports.GitStagedChangesReader
import commitool.domain.models.{ChangeStatus, CommitValidationError, GitError, StagedChange}
import scala.sys.process._
import scala.util.matching.Regex

class ShellStagedChangesReader extends GitStagedChangesReader {
  private val ChangeStatusRegex: Regex = "^(A|M|D)\\t(.+)$".r

  override def readStagedChanges(): Either[CommitValidationError, List[StagedChange]] = {
    try {
      val stdout = new StringBuilder
      val stderr = new StringBuilder

      val processLogger = ProcessLogger(
        (o: String) => stdout.append(o).append("\n"),
        (e: String) => stderr.append(e).append("\n")
      )

      val exitCode = "git diff --cached --name-status" ! processLogger

      if (exitCode == 0) {
        val changes = stdout.toString.split("\n").filter(_.trim.nonEmpty).flatMap {
          case ChangeStatusRegex(statusChar, filePath) =>
            val changeStatus = statusChar match {
              case "A" => ChangeStatus.Added
              case "M" => ChangeStatus.Modified
              case "D" => ChangeStatus.Deleted
              case _ => null // Should not happen with the regex
            }
            Option(changeStatus).map(s => StagedChange(s, filePath))
          case _ => None
        }.toList
        Right(changes)
      } else {
        Left(GitError(stderr.toString.trim))
      }
    } catch {
      case e: Exception => Left(GitError(s"Failed to execute git command: ${e.getMessage}"))
    }
  }
}
