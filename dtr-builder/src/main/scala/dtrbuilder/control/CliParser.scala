package dtrbuilder.control

import dtrbuilder.domain.DtrConfig
import java.nio.file.{Path, Paths}

/** CLI argument parser for the DTR builder.
  *
  * Supported args:
  *   --root <path>           Root directory to analyze (default: cwd)
  *   --out <path>            Output file path template (required)
  *   --max-chunk-size <bytes> Override DTR_MAX_CHUNK_SIZE (optional)
  *   --filter <glob>         Additional blocklist patterns (optional, repeatable)
  *   --no-dotenv             Skip .env discovery (optional flag)
  *   --version               Print version
  *   --help                  Print usage
  */
object CliParser {

  /** Parse CLI args into a DtrConfig or print help/version and exit. */
  def parse(args: Array[String]): DtrConfig = {
    var root: Path = Paths.get(".").toAbsolutePath.normalize
    var output: Option[Path] = None
    var maxChunkSize: Option[Long] = None
    var filters: Seq[String] = Seq.empty
    var noDotenv = false
    var showHelp = false
    var showVersion = false

    var i = 0
    while (i < args.length) {
      args(i) match {
        case "--root" =>
          i += 1
          if (i < args.length) root = Paths.get(args(i)).toAbsolutePath.normalize
          else printErrorAndExit("--root requires a path argument")
        case "--out" =>
          i += 1
          if (i < args.length) output = Some(Paths.get(args(i)))
          else printErrorAndExit("--out requires a path argument")
        case "--max-chunk-size" =>
          i += 1
          if (i < args.length) {
            maxChunkSize = Some(args(i).toLong)
          } else printErrorAndExit("--max-chunk-size requires a byte argument")
        case "--filter" =>
          i += 1
          if (i < args.length) filters = filters :+ args(i)
          else printErrorAndExit("--filter requires a glob pattern")
        case "--no-dotenv" =>
          noDotenv = true
        case "--version" =>
          showVersion = true
        case "--help" | "-h" =>
          showHelp = true
        case unknown =>
          printErrorAndExit(s"Unknown argument: $unknown")
      }
      i += 1
    }

    if (showHelp) { printHelp(); sys.exit(0) }
    if (showVersion) { println(s"dtr-builder version ${Version}"); sys.exit(0) }

    val outPath = output.getOrElse {
      printErrorAndExit("--out <path> is required")
      // unreachable:
      Paths.get("dtr.itr")
    }

    DtrConfig(
      pathRoot = root,
      outputPath = outPath,
      maxChunkSize = maxChunkSize.getOrElse(DtrConfig.defaultMaxChunkSize),
      chunkEnabled = maxChunkSize.isDefined || true, // always enable chunking by default
      noDotenv = noDotenv,
      additionalFilters = filters
    )
  }

  private def printHelp(): Unit = {
    println(
      s"""dtr-builder v${Version} — Automated Extractor (X node)
         |
         |Usage: dtr-builder --out <path> [options]
         |
         |Required:
         |  --out <path>          Output file path template
         |
         |Options:
         |  --root <path>         Root directory to analyze (default: current directory)
         |  --max-chunk-size <n>  Max chunk size in bytes (default: 1MB, or DTR_MAX_CHUNK_SIZE env var)
         |  --filter <glob>       Additional blocklist pattern (repeatable)
         |  --no-dotenv           Skip .env file discovery
         |  --version             Print version and exit
         |  --help                Print this help and exit
         |
         |Output:
         |  Single chunk:  <path>
         |  Multiple chunks: <path>-001.ext, <path>-002.ext, ...
         |""".stripMargin
    )
  }

  private def printErrorAndExit(msg: String): Nothing = {
    System.err.println(s"Error: $msg")
    System.err.println("Usage: dtr-builder --out <path> [--root <path>] [options]")
    sys.exit(1)
    throw new IllegalStateException("unreachable")
  }

  val Version: String = "0.1.0"
}
