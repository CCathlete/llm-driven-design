package dtrbuilder.control

import dtrbuilder.application.Environment
import dtrbuilder.domain.DtrConfig
import dtrbuilder.infrastructure.FileSystemWalker

/** Main entry point for the DTR Builder.
  *
  * Flow:
  * 1. Parse CLI args into DtrConfig
  * 2. Build dependency container
  * 3. DotEnvLoader: discover and load .env (unless --no-dotenv)
  * 4. Merge loaded dotenv into Environment
  * 5. Run DtrBuilderService pipeline
  * 6. Report results or errors
  * 7. Exit with status code
  */
object DtrApp {

  def main(args: Array[String]): Unit = {
    try {
      // Step 1: Parse CLI args
      val config = CliParser.parse(args)

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

      // Step 7: Exit success
      sys.exit(0)
    } catch {
      case e: IllegalArgumentException =>
        System.err.println(s"Error: ${e.getMessage}")
        System.err.println("Usage: dtr-builder --out <path> [--root <path>] [options]")
        sys.exit(1)
      case e: Exception =>
        System.err.println(s"Fatal error: ${e.getMessage}")
        e.printStackTrace(System.err)
        sys.exit(2)
    }
  }
}
