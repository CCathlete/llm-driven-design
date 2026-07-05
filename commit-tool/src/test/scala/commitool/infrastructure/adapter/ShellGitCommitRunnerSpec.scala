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
  private val commitRunner = new ShellGitCommitRunner

  override def beforeEach(): Unit = {
    tempDir = Files.createTempDirectory("commit-tool-test")
    // Initialize git repository
    Seq("git", "init").!(ProcessLogger(_ => ()))
    // Change to temp directory
    System.setProperty("user.dir", tempDir.toString)
    // Create a test file
    Files.write(tempDir.resolve("test.txt"), "test content".getBytes)
    // Stage the test file
    Seq("git", "add", "test.txt").!(ProcessLogger(_ => ()))
  }

  override def afterEach(): Unit = {
    // Clean up
    if (tempDir != null) {
      val _ = Seq("rm", "-rf", tempDir.toString).!(ProcessLogger(_ => ()))
    }
  }

  "ShellGitCommitRunner.commit" should "succeed with valid commit message" in {
    val commitMessage = "feat(scope): test commit\n\nTest commit body"
    commitRunner.commit(commitMessage) should matchPattern {
      case Right(_) =>
    }

    // Verify the commit was created
    val logOutput = Seq("git", "log", "--oneline").!!.trim
    logOutput should include regex """feat\(scope\): test commit"""
  }

  it should "fail with empty commit message" in {
    val emptyMessage = ""
    commitRunner.commit(emptyMessage) should matchPattern {
      case Left(_) =>
    }
  }

  it should "fail with invalid commit message format" in {
    val invalidMessage = "invalid message"
    commitRunner.commit(invalidMessage) should matchPattern {
      case Left(_) =>
    }
  }
}
