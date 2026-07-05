package commitool.control.di

import commitool.application.use_cases.CommitUseCase
import commitool.infrastructure.adapter.{FileSystemContentReader, ShellGitCommitRunner, ShellStagedChangesReader}

class Container {
  private val gitStagedChangesReader = new ShellStagedChangesReader
  private val gitCommitRunner = new ShellGitCommitRunner
  private val fileContentReader = new FileSystemContentReader

  val commitUseCase = new CommitUseCase(
    gitStagedChangesReader,
    gitCommitRunner,
    fileContentReader
  )
}
