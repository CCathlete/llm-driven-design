package commitool.application.use_cases

import commitool.application.ports.{FileContentReader, GitCommitRunner, GitStagedChangesReader}
import commitool.application.use_cases.CommitUseCase
import commitool.domain.models._
import org.scalamock.scalatest.MockFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CommitUseCaseSpec extends AnyFlatSpec with Matchers with MockFactory {

  val mockGitReader = mock[GitStagedChangesReader]
  val mockCommitRunner = mock[GitCommitRunner]
  val mockFileReader = mock[FileContentReader]

  val commitUseCase = new CommitUseCase(
    mockGitReader,
    mockCommitRunner,
    mockFileReader
  )

  "CommitUseCase.execute" should "succeed with happy path" in {
    val messageFilePath = "/path/to/message.txt"
    val messageContent = "feat(scope): description\n\nbody content"
    val stagedChanges = List(
      StagedChange(ChangeStatus.Added, "file1.txt")
    )

    (mockFileReader.read _).expects(messageFilePath).returning(Right(messageContent))
    (mockGitReader.readStagedChanges _).expects().returning(Right(stagedChanges))
    (mockCommitRunner.commit _).expects(*).returning(Right(()))

    commitUseCase.execute(messageFilePath) should matchPattern {
      case Right(_) =>
    }
  }

  it should "return FileNotFound error when message file not found" in {
    val messageFilePath = "/path/to/missing.txt"

    (mockFileReader.read _).expects(messageFilePath).returning(Left(FileNotFound(messageFilePath)))

    commitUseCase.execute(messageFilePath) should matchPattern {
      case Left(FileNotFound(path)) if path == messageFilePath =>
    }
  }

  it should "return InvalidFormat error for invalid message" in {
    val messageFilePath = "/path/to/invalid.txt"
    val invalidMessage = "invalid message"

    (mockFileReader.read _).expects(messageFilePath).returning(Right(invalidMessage))

    commitUseCase.execute(messageFilePath) should matchPattern {
      case Left(InvalidFormat(line)) if line == invalidMessage =>
    }
  }

  it should "return NoStagedChanges error when no changes staged" in {
    val messageFilePath = "/path/to/message.txt"
    val messageContent = "feat(scope): description\n\nbody content"

    (mockFileReader.read _).expects(messageFilePath).returning(Right(messageContent))
    (mockGitReader.readStagedChanges _).expects().returning(Right(Nil))

    commitUseCase.execute(messageFilePath) should matchPattern {
      case Left(NoStagedChanges) =>
    }
  }

  it should "return GitError when commit fails" in {
    val messageFilePath = "/path/to/message.txt"
    val messageContent = "feat(scope): description\n\nbody content"
    val stagedChanges = List(
      StagedChange(ChangeStatus.Added, "file1.txt")
    )
    val gitError = GitError("git commit failed")

    (mockFileReader.read _).expects(messageFilePath).returning(Right(messageContent))
    (mockGitReader.readStagedChanges _).expects().returning(Right(stagedChanges))
    (mockCommitRunner.commit _).expects(*).returning(Left(gitError))

    commitUseCase.execute(messageFilePath) should matchPattern {
      case Left(GitError(msg)) if msg == gitError.message =>
    }
  }
}
