package commitool.control.cli

object CliParser {
  private val Usage = """
    Usage: commit-tool --message-file <path> [--help]
    
    Options:
      --message-file <path>  Path to the file containing the commit message
      --help                 Show this help message
    """

  def parse(args: Array[String]): String = {
    if (args.contains("--help")) {
      println(Usage)
      sys.exit(0)
    }

    val messageFileIndex = args.indexOf("--message-file")
    if (messageFileIndex == -1 || messageFileIndex == args.length - 1) {
      Console.err.println("Error: --message-file argument is required")
      Console.err.println(Usage)
      sys.exit(1)
    }

    args(messageFileIndex + 1)
  }
}
