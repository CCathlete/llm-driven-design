package dtrbuilder.control

import dtrbuilder.application._
import dtrbuilder.domain.{CodexEntry, FileEntry}
import dtrbuilder.infrastructure._
import dtrbuilder.infrastructure.ast._

/** Manual dependency injection container.
  *
  * Wiring order (respecting DIP and NO_CROSS_LAYER):
  *   1. DotEnvLoader (infrastructure)
  *   2. Environment (infrastructure singleton)
  *   3. SeedTemplateLoader (infrastructure) — loads seed template from classpath
  *   4. LanguageProfileLoader (infrastructure) — loads profiles from classpath
  *   5. LanguageDetector (application service) — uses loaded profiles
  *   6. FileSystemWalker (infrastructure)
  *   7. FileMetadataAnalyzer (infrastructure)
  *   8. RegexSignatureExtractor (infrastructure) — baseline for all languages
  *   9. AST extractors (infrastructure, with regex fallback on parse failure)
  *   10. SignatureExtractor composite (wraps regex + AST with proper precedence)
  *   11. RelationDetector (application service)
  *   12. DtrFormatter (application service)
  *   13. DtrChunker (application service)
  *   14. FileSystemDtrWriter (infrastructure adapter for DtrWriter port)
  *   15. DtrBuilderService (application service)
  */
class Container {

  // Infrastructure: DotEnvLoader
  val dotEnvLoader: DotEnvLoader = new FileSystemDotEnvLoader

  // Infrastructure: Environment singleton
  val environment: Environment = EnvironmentImpl

  // Infrastructure: LanguageProfileLoader — loads from classpath resources
  val languageProfileLoader: LanguageProfileLoader = new LanguageProfileLoader

  // Application: LanguageDetector — uses loaded profiles with fallback to defaults
  val languageDetector: LanguageDetector = LanguageDetector.createWithProfileLoader()

  // Infrastructure: FileSystemWalker
  val fileSystemWalker: FileWalker = new FileSystemWalker()

  // Infrastructure: FileMetadataAnalyzer
  val fileAnalyzer: FileAnalyzer = new FileMetadataAnalyzer

  // Infrastructure: RegexSignatureExtractor (baseline for all languages)
  val regexExtractor: RegexSignatureExtractor = new RegexSignatureExtractor

  // Infrastructure: AST extractors with regex fallback on parse failure
  val scalaAstExtractor: ScalaAstExtractor         = new ScalaAstExtractor(regexExtractor)
  val javaAstExtractor: JavaAstExtractor           = new JavaAstExtractor(regexExtractor)
  val pythonAstExtractor: PythonAstExtractor       = new PythonAstExtractor(regexExtractor)
  val javaScriptAstExtractor: JavaScriptAstExtractor = new JavaScriptAstExtractor(regexExtractor)
  val typeScriptAstExtractor: TypeScriptAstExtractor = new TypeScriptAstExtractor(regexExtractor)

  /** Composite SignatureExtractor that dispatches to AST extractors when available,
    * falling back to regex for all other languages.
    *
    * Precedence: AST attempted first, regex fallback on failure.
    */
  val signatureExtractor: SignatureExtractor = new SignatureExtractor {
    private val astExtractors: Map[String, SignatureExtractor] = Map(
      "Scala"       -> scalaAstExtractor,
      "Java"        -> javaAstExtractor,
      "Python"      -> pythonAstExtractor,
      "JavaScript"  -> javaScriptAstExtractor,
      "TypeScript"  -> typeScriptAstExtractor
    )

    override def extract(entry: FileEntry): Seq[CodexEntry] = {
      entry.language match {
        case Some(lang) =>
          astExtractors.get(lang.name) match {
            case Some(astExtractor) => astExtractor.extract(entry)
            case None               => regexExtractor.extract(entry)
          }
        case None => regexExtractor.extract(entry)
      }
    }
  }

  // Application: RelationDetector
  val relationDetector: RelationDetector = new RelationDetector

  // Application: DtrFormatter
  val dtrFormatter: DtrFormatter = new DtrFormatter

  // Application: DtrChunker
  val dtrChunker: DtrChunker = new DtrChunker

  // Infrastructure: FileSystemDtrWriter (implements application.DtrWriter port)
  val dtrWriter: DtrWriter = new FileSystemDtrWriter

  // Application: DtrBuilderService (wired with all dependencies)
  val dtrBuilderService: DtrBuilderService = new DtrBuilderService(
    dotEnvLoader    = dotEnvLoader,
    fileWalker      = fileSystemWalker,
    fileAnalyzer    = fileAnalyzer,
    languageDetector = languageDetector,
    signatureExtractor = signatureExtractor,
    relationDetector   = relationDetector,
    dtrFormatter       = dtrFormatter,
    dtrChunker         = dtrChunker,
    dtrWriter          = dtrWriter
  )
}
