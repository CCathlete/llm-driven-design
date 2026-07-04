package dtrbuilder.application

import dtrbuilder.domain._
import java.nio.file.Path

/** Main pipeline orchestrator for the DTR builder.
  *
  * Steps (in order):
  * 1. LOAD_DOTENV     — init DotEnvLoader, discover and load .env
  * 2. WALK            — walk file tree, yield FileEntry stream
  * 3. ANALYZE         — enrich each FileEntry with metadata
  * 4. EXTRACT         — extract code signatures
  * 5. DETECT_RELATIONS — infer dependency edges
  * 6. FORMAT          — transform domain objects into DTR lines
  * 7. CHUNK           — split at file boundaries when exceeding max size
  * 8. WRITE           — write chunks to disk
  */
class DtrBuilderService(
    dotEnvLoader: DotEnvLoader,
    fileWalker: FileWalker,
    fileAnalyzer: FileAnalyzer,
    languageDetector: LanguageDetector,
    signatureExtractor: SignatureExtractor,
    relationDetector: RelationDetector,
    dtrFormatter: DtrFormatter,
    dtrChunker: DtrChunker,
    dtrWriter: DtrWriter
) {

  /** Run the full DTR building pipeline.
    *
    * @param config     the DtrConfig with root, output, chunking settings
    * @param env        the Environment singleton with merged env vars
    * @return           set of written output file paths
    */
  def build(config: DtrConfig, env: Environment): BuildResult = {
    // STEP 1: LOAD_DOTENV — discover and load .env if enabled
    val dotEnv: DotEnv =
      if (config.noDotenv) DotEnv(Map.empty, None)
      else {
        val loaded = dotEnvLoader.load(config.pathRoot)
        loaded.mergedWithSystem(env.toMap)
      }

    // Merge dotenv vars into environment
    dotEnv.vars.foreach { case (k, v) => env.set(k, v) }

    // Determine effective max chunk size:
    // 1. From DtrConfig (CLI override), 2. From env var, 3. Default
    val effectiveMaxChunkSize = {
      if (config.maxChunkSize != DtrConfig.defaultMaxChunkSize) config.maxChunkSize
      else env.get("DTR_MAX_CHUNK_SIZE").map(_.toLong).getOrElse(DtrConfig.defaultMaxChunkSize)
    }

    // STEP 2: WALK — get file tree
    val fileEntries = fileWalker.walk(config.pathRoot)

    // Process files sequentially
    val fileList = fileEntries.toVector

    // STEP 3: ANALYZE — enrich each FileEntry
    val analyzed = fileList.map(fe => fileAnalyzer.analyze(fe))

    // STEP 3b: Detect language for each file (part of analysis)
    val withLang = analyzed.map { fe =>
      val lang = languageDetector.detect(fe)
      fe.copy(language = Some(lang))
    }

    // STEP 4: EXTRACT — extract code signatures
    val allCodexEntries = withLang.flatMap { fe =>
      if (!fe.isDirectory) signatureExtractor.extract(fe)
      else Seq.empty
    }

    // Build type definitions from codex entries
    val typeDefs = allCodexEntries.collect {
      case ce @ CodexEntry(_, SigType.CLASS | SigType.TRAIT | SigType.OBJECT | SigType.INTERFACE | SigType.ENUM, name) =>
        TypeDef(
          fullyQualifiedName = s"${ce.relPath.replace('/', '.')}.$name",
          kind = ce.sigType.entryName,
          sourceFile = ce.relPath
        )
    }

    // STEP 5: DETECT_RELATIONS
    val relations = relationDetector.detect(allCodexEntries, typeDefs)

    // Build meta
    val meta = Map(
      "GENERATOR" -> "dtr-builder",
      "VERSION" -> "0.1.0",
      "TIMESTAMP" -> java.time.Instant.now().toString,
      "FILE_COUNT" -> withLang.size.toString,
      "CODEX_COUNT" -> allCodexEntries.size.toString,
      "TYPE_COUNT" -> typeDefs.size.toString,
      "RELATION_COUNT" -> relations.size.toString,
      "ROOT" -> config.pathRoot.toAbsolutePath.normalize.toString
    )

    // STEP 6: FORMAT — transform to raw DTR lines
    val allRawEntries = withLang.flatMap { fe =>
      val fileCodex = allCodexEntries.filter(_.relPath == fe.relPath)
      val fileTypes = typeDefs.filter(_.sourceFile == fe.relPath)
      val fileRels  = relations.filter(r => r.fromFqn.contains(fe.relPath.replace('/', '.')) || r.toFqn.contains(fe.relPath.replace('/', '.')))
      dtrFormatter.format(fe, fileCodex, fileTypes, fileRels, config)
    }

    // Prepend meta entries
    val metaEntries = meta.map { case (k, v) => RawEntry(s"META.$k=$v") }
    val allEntries = metaEntries.iterator ++ allRawEntries

    // STEP 7: CHUNK
    val chunks = dtrChunker.chunk(allEntries, effectiveMaxChunkSize)

    // STEP 8: WRITE
    val written = dtrWriter.write(chunks, config.outputPath)

    BuildResult(
      outputPaths = written,
      fileCount = withLang.size,
      codexCount = allCodexEntries.size,
      typeCount = typeDefs.size,
      relationCount = relations.size,
      chunkCount = chunks.size,
      dotEnv = dotEnv
    )
  }
}

/** Result of a DTR build operation. */
final case class BuildResult(
    outputPaths: Seq[Path],
    fileCount: Int,
    codexCount: Int,
    typeCount: Int,
    relationCount: Int,
    chunkCount: Int,
    dotEnv: DotEnv
)
