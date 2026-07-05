package dtrbuilder.control

import dtrbuilder.application.Environment
import dtrbuilder.domain.DtrConfig
import dtrbuilder.infrastructure.FileSystemWalker

/** Main entry point for the DTR Builder.
  *
  * Flow (ExtractMode):
  * 1. Parse CLI args into DtrConfig
  * 2. Build dependency container
  * 3. DotEnvLoader: discover and load .env (unless --no-dotenv)
  * 4. Merge loaded dotenv into Environment
  * 5. Run DtrBuilderService pipeline
  * 6. Report results or errors
  * 7. Exit with status code
  *
  * Flow (CreateBaselineMode):
  * 1. Parse CLI args into DtrConfig with CreateBaselineMode params
  * 2. Instantiate SeedTemplateLoader and BaselineDtrGenerator
  * 3. Generate baseline DTR
  * 4. Write via DtrWriter
  * 5. Report output
  */
object DtrApp {

  def main(args: Array[String]): Unit = {
    try {
      // Step 1: Parse CLI args
      val config = CliParser.parse(args)

      config.mode match {
        case DtrConfig.ExtractMode =>
          runExtractMode(config)

        case baseline: DtrConfig.CreateBaselineMode =>
          runBaselineMode(config, baseline)
      }

      sys.exit(0)
    } catch {
      case e: IllegalArgumentException =>
        System.err.println(s"Error: ${e.getMessage}")
        System.err.println("Usage: dtr-builder --out <path> [--root <path>] [options]")
        System.err.println("   or: dtr-builder --create-baseline-dtr --app-name <name> --package <pkg> [options]")
        sys.exit(1)
      case e: Exception =>
        System.err.println(s"Fatal error: ${e.getMessage}")
        e.printStackTrace(System.err)
        sys.exit(2)
    }
  }

  /** Run the existing extraction pipeline. */
  private def runExtractMode(config: DtrConfig): Unit = {
    // Step 2: Build DI container
    val container = new Container()
    val env: Environment = container.environment

    // Step 3 & 4: Load .env (if enabled) and merge into environment
    if (!config.noDotenv) {
      val dotEnv = container.dotEnvLoader.load(config.pathRoot)
      dotEnv.vars.foreach { case (k, v) => env.set(k, v) }
      if (dotEnv.sourcePath.isDefined) {
        println(s"Loaded .env from: ${dotEnv.sourcePath.get}")
      }
    }

    // Add any CLI-provided max-chunk-size override
    if (config.maxChunkSize != DtrConfig.defaultMaxChunkSize) {
      env.set("DTR_MAX_CHUNK_SIZE", config.maxChunkSize.toString)
    }

    // Add additional filter patterns
    if (config.additionalFilters.nonEmpty) {
      FileSystemWalker.addToBlocklist(config.additionalFilters)
    }

    // Step 5: Run the pipeline
    println(s"DTR Builder v${CliParser.Version}")
    println(s"Root:  ${config.pathRoot}")
    println(s"Out:   ${config.outputPath}")
    println(s"Walking file tree and extracting signatures...")

    val result = container.dtrBuilderService.build(config, env)

    // Step 6: Report results
    println()
    println("=== DTR Build Complete ===")
    println(s"Files analyzed:     ${result.fileCount}")
    println(s"Codex entries:      ${result.codexCount}")
    println(s"Type definitions:   ${result.typeCount}")
    println(s"Relations detected: ${result.relationCount}")
    println(s"Chunks written:     ${result.chunkCount}")
    println(s"Output paths:")
    result.outputPaths.foreach(p => println(s"  - $p"))
  }

  /** Run the baseline DTR generation mode. */
  private def runBaselineMode(config: DtrConfig, baseline: DtrConfig.CreateBaselineMode): Unit = {
    println(s"DTR Builder v${CliParser.Version} — Baseline Generation Mode")
    println(s"App:     ${baseline.appName}")
    println(s"Package: ${baseline.packageName}")
    println(s"Lang:    ${baseline.language}")
    println(s"Out:     ${config.outputPath}")
    println()

    // Instantiate and wire
    val container = new Container()
    val generator = new dtrbuilder.application.BaselineDtrGenerator(container.seedTemplateLoader)

    // Generate baseline DTR entries
    println("Loading seed template and generating baseline DTR...")
    val entries = generator.generate(
      appName     = baseline.appName,
      packageName = baseline.packageName,
      rootPath    = config.pathRoot.toString,
      language    = baseline.language
    )

    println(s"Generated ${entries.size} entries.")

    // Write via DtrWriter
    val chunk = dtrbuilder.domain.DtrChunk(
      entries     = entries,
      byteSize    = entries.map(_.tensorLine.length + 1).sum.toLong,
      chunkIndex  = 0,
      totalChunks = 1
    )

    val written = container.dtrWriter.write(Seq(chunk), config.outputPath)

    println()
    println("=== Baseline DTR Generated ===")
    println(s"Entries:   ${entries.size}")
    println(s"Output:")
    written.foreach(p => println(s"  - $p"))
  }
}
