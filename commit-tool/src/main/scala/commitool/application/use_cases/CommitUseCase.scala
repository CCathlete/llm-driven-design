package commitool.application.use_cases

import commitool.application.ports.{FileContentReader, GitCommitRunner, GitStagedChangesReader}
import commitool.domain.models.{Changelist, CommitMessage, CommitValidationError, NoStagedChanges}

class CommitUseCase(
  gitReader: GitStagedChangesReader,
  commitRunner: GitCommitRunner,
  fileReader: FileContentReader
) {

  def execute(messageFilePath: String): Either[CommitValidationError, Unit] = {
    for {
      content <- fileReader.read(messageFilePath)
      commitMessage <- CommitMessage.fromRaw(content)
      stagedChanges <- gitReader.readStagedChanges().flatMap {
        case Nil => Left(NoStagedChanges)
        case changes => Right(changes)
      }
      changelist = Changelist(stagedChanges)
      finalMessage = commitMessage.compose(changelist)
      _ <- commitRunner.commit(finalMessage)
    } yield ()
  }
}
