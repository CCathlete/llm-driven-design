package commitool.control.entrypoint

import commitool.control.cli.CliParser
import commitool.control.di.Container

object CommitToolApp extends App {
  val messageFilePath = CliParser.parse(args)
  val container = new Container

  container.commitUseCase.execute(messageFilePath) match {
    case Left(error) =>
      Console.err.println(error.message)
      sys.exit(1)
    case Right(_) =>
      println("commit-tool: committed successfully.")
      sys.exit(0)
  }
}
