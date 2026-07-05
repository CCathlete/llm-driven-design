package dtrbuilder.application.use_cases

import dtrbuilder.domain.models._
import dtrbuilder.domain.models.BuildResult
import dtrbuilder.domain.models.DotEnv
import dtrbuilder.application.ports._
import dtrbuilder.application.services._
import java.nio.file.Path

class DtrPipeline(
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

  def build(config: DtrConfig, env: Environment): BuildResult = {
    val dotEnv: DotEnv =
      if (config.noDotenv) DotEnv(Map.empty, None)
      else {
        val loaded = dotEnvLoader.load(config.pathRoot)
        loaded.mergedWithSystem(env.toMap)
      }

    dotEnv.vars.foreach { case (k, v) => env.set(k, v) }

    val effectiveMaxChunkSize = {
      if (config.maxChunkSize != DtrConfig.defaultMaxChunkSize) config.maxChunkSize
      else env.get("DTR_MAX_CHUNK_SIZE").map(_.toLong).getOrElse(DtrConfig.defaultMaxChunkSize)
    }

    val fileEntries = fileWalker.walk(config.pathRoot)

    val fileList = fileEntries.toVector

    val analyzed = fileList.map(fe => fileAnalyzer.analyze(fe))

    val withLang = analyzed.map { fe =>
      val lang = languageDetector.detect(fe)
      fe.copy(language = Some(lang))
    }

    val allCodexEntries = withLang.flatMap { fe =>
      if (!fe.isDirectory) signatureExtractor.extract(fe)
      else Seq.empty
    }

    val typeDefs = allCodexEntries.collect {
      case ce @ CodexEntry(_, SigType.CLASS | SigType.TRAIT | SigType.OBJECT | SigType.INTERFACE | SigType.ENUM, name) =>
        TypeDef(
          fullyQualifiedName = s"${ce.relPath.replace('/', '.')}.$name",
          kind = ce.sigType.entryName,
          sourceFile = ce.relPath
        )
    }

    val relations = relationDetector.detect(allCodexEntries, typeDefs)

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

    val allRawEntries = withLang.flatMap { fe =>
      val fileCodex = allCodexEntries.filter(_.relPath == fe.relPath)
      val fileTypes = typeDefs.filter(_.sourceFile == fe.relPath)
      val fileFqn  = fe.relPath.replace('/', '.')
      val fileRels = relations.filter(r =>
        r.fromFqn == fileFqn || r.fromFqn.startsWith(fileFqn + ".") || r.toFqn.contains(fileFqn)
      )
      dtrFormatter.format(fe, fileCodex, fileTypes, fileRels, config)
    }

    val metaEntries = meta.map { case (k, v) => RawEntry(s"META.$k=$v") }
    val allEntries = metaEntries.iterator ++ allRawEntries

    val chunks = dtrChunker.chunk(allEntries, effectiveMaxChunkSize)

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
