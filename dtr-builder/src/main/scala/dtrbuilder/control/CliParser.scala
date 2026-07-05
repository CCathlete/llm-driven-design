package dtrbuilder.control

import dtrbuilder.domain.DtrConfig
import java.nio.file.{Path, Paths}

/** CLI argument parser for the DTR builder.
  *
  * Supported args:
  *   --root <path>           Root directory (extract) / DTR root path (baseline)
  *   --out <path>            Output file path template (required for extract mode)
  *   --max-chunk-size <bytes> Override DTR_MAX_CHUNK_SIZE (optional)
  *   --filter <glob>         Additional blocklist patterns (optional, repeatable)
  *   --no-dotenv             Skip .env discovery (optional flag)
  *   --create-baseline-dtr   Activate baseline DTR generation mode
  *   --app-name <name>       Application name (baseline mode, required)
  *   --language <lang>       Primary language (baseline mode, default: Scala)
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
    var createBaseline = false
    var appName: Option[String] = None
    var language: String = "Scala"

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
        case "--create-baseline-dtr" =>
          createBaseline = true
        case "--app-name" =>
          i += 1
          if (i < args.length) appName = Some(args(i))
          else printErrorAndExit("--app-name requires a name argument")
        case "--language" =>
          i += 1
          if (i < args.length) language = args(i)
          else printErrorAndExit("--language requires a language argument")
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

    if (createBaseline) {
      val name = appName.getOrElse(printErrorAndExit("--app-name <name> is required in --create-baseline-dtr mode"))

      DtrConfig(
        pathRoot = root,
        outputPath = output.getOrElse(Paths.get(s"$name.dtr")),
        maxChunkSize = maxChunkSize.getOrElse(DtrConfig.defaultMaxChunkSize),
        chunkEnabled = false,
        noDotenv = true,
        additionalFilters = filters,
        mode = DtrConfig.CreateBaselineMode(
          appName = name,
          language = language
        )
      )
    } else {
      val outPath = output.getOrElse {
        printErrorAndExit("--out <path> is required")
        Paths.get("dtr.itr")
      }

      DtrConfig(
        pathRoot = root,
        outputPath = outPath,
        maxChunkSize = maxChunkSize.getOrElse(DtrConfig.defaultMaxChunkSize),
        chunkEnabled = maxChunkSize.isDefined || true,
        noDotenv = noDotenv,
        additionalFilters = filters
      )
    }
  }

  private def printHelp(): Unit = {
    println(
      s"""dtr-builder v${Version} — Automated Extractor (X node)
         |
         |Usage: dtr-builder --out <path> [options]
         |   or: dtr-builder --create-baseline-dtr --app-name <name> [options]
         |
         |Extract mode (default):
         |  --out <path>          Output file path template
         |  --root <path>         Root directory to analyze (default: current directory)
         |  --max-chunk-size <n>  Max chunk size in bytes (default: 1MB, or DTR_MAX_CHUNK_SIZE env var)
         |  --filter <glob>       Additional blocklist pattern (repeatable)
         |  --no-dotenv           Skip .env file discovery
         |
         |Baseline generation mode:
         |  --create-baseline-dtr Activate baseline DTR generation mode
         |  --app-name <name>     Application name (required)
         |  --root <path>         DTR root path for generated output (default: current directory)
         |  --language <lang>     Primary language (default: Scala)
         |  --out <path>          Output path (default: <app-name>.dtr)
         |
         |Common:
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
    System.err.println("   or: dtr-builder --create-baseline-dtr --app-name <name> --package <pkg> [options]")
    sys.exit(1)
    throw new IllegalStateException("unreachable")
  }

  val Version: String = "0.1.1"
}
