package commitool.infrastructure.adapter

import commitool.infrastructure.adapter.ShellGitCommitRunner
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.nio.file.{Files, Path}
import scala.sys.process.Process
import scala.sys.process.ProcessLogger

class ShellGitCommitRunnerSpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach {

  private var tempDir: Path = _
  private var commitRunner: ShellGitCommitRunner = _

  override def beforeEach(): Unit = {
    tempDir = Files.createTempDirectory("commit-tool-test")
    commitRunner = new ShellGitCommitRunner(Some(tempDir.toFile))
    // Initialize git repository
    Process(Seq("git", "init"), tempDir.toFile) ! ProcessLogger(_ => ())
    // Configure git user for test commits
    Process(Seq("git", "config", "user.name", "Test User"), tempDir.toFile) ! ProcessLogger(_ => ())
    Process(Seq("git", "config", "user.email", "test@example.com"), tempDir.toFile) ! ProcessLogger(_ => ())
    // Create a test file
    Files.write(tempDir.resolve("test.txt"), "test content".getBytes)
    // Stage the test file
    Process(Seq("git", "add", "test.txt"), tempDir.toFile) ! ProcessLogger(_ => ())
  }

  override def afterEach(): Unit = {
    // Clean up
    if (tempDir != null) {
      Process(Seq("rm", "-rf", tempDir.toString)) ! ProcessLogger(_ => ())
    }
  }

  "ShellGitCommitRunner.commit" should "succeed with valid commit message" in {
    val commitMessage = "feat(scope): test commit\n\nTest commit body"
    commitRunner.commit(commitMessage) should matchPattern {
      case Right(_) =>
    }

    // Verify the commit was created
    val logOutput = Process(Seq("git", "log", "--oneline"), tempDir.toFile).!!.trim
    logOutput should include regex """feat\(scope\): test commit"""
  }

  it should "fail with empty commit message" in {
    val emptyMessage = ""
    commitRunner.commit(emptyMessage) should matchPattern {
      case Left(_) =>
    }
  }

  it should "commit a non-empty message regardless of format" in {
    val invalidMessage = "invalid message"
    commitRunner.commit(invalidMessage) should matchPattern {
      case Right(_) => // git accepts any non-empty message
    }
  }
}
