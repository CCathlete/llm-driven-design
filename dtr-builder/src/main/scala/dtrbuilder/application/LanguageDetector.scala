package dtrbuilder.application

import dtrbuilder.domain.{FileEntry, Language, RegexPattern, SigType}
import dtrbuilder.infrastructure.LanguageProfileLoader

/** Application service: detects programming language from file extension and shebang.
  * Maintains a registry of known language profiles and matches files against them.
  *
  * Uses LanguageProfileLoader to load profiles from classpath resources,
  * falling back to hardcoded defaults if none are available.
  */
class LanguageDetector(languages: Seq[Language]) {

  private val byExtension: Map[String, Language] =
    languages.flatMap(l => l.extensions.map(ext => ext.toLowerCase -> l)).toMap

  /** Detect language from a FileEntry by checking extension then shebang. */
  def detect(entry: FileEntry): Language = {
    detectFromExtension(entry.extension)
      .orElse(detectFromContent(entry.absolutePath.toString))
      .getOrElse(LanguageDetector.unknownLanguage)
  }

  /** Detect language by file extension. */
  def detectFromExtension(extension: String): Option[Language] =
    byExtension.get(extension.stripPrefix(".").toLowerCase)

  /** Detect language by shebang line (first line of file if it starts with #!). */
  def detectFromShebang(shebang: String): Option[Language] =
    languages.find { lang =>
      lang.shebangs.exists { pattern =>
        val shebangPattern = pattern.stripPrefix("#!").trim.r
        shebangPattern.findFirstIn(shebang).isDefined
      }
    }

  /** Attempt to read the first line of a file and detect by shebang. */
  private def detectFromContent(filePath: String): Option[Language] = {
    try {
      val source = scala.io.Source.fromFile(filePath)
      try {
        val firstLine = source.getLines().nextOption().getOrElse("")
        if (firstLine.startsWith("#!")) detectFromShebang(firstLine) else None
      } finally {
        source.close()
      }
    } catch {
      case _: Exception => None
    }
  }
}

object LanguageDetector {
  val unknownLanguage: Language = Language(
    name = "Unknown",
    extensions = Seq.empty,
    shebangs = Seq.empty,
    regexPatterns = Seq.empty
  )

  /** Create a LanguageDetector loading profiles from classpath, falling back to defaults. */
  def createWithProfileLoader(): LanguageDetector = {
    val loader = new LanguageProfileLoader
    val loaded = loader.loadAll()
    val profiles = if (loaded.nonEmpty) loaded else defaultLanguages
    new LanguageDetector(profiles)
  }

  /** Build default language profiles for all supported languages.
    * These are used as fallback if classpath resources are unavailable.
    */
  def defaultLanguages: Seq[Language] = Seq(
    Language("Scala", Seq("scala"), Seq.empty, Seq(
      RegexPattern(SigType.PACKAGE, """^package\s+([\w.]+)""".r),
      RegexPattern(SigType.IMPORT, """^import\s+([\w.]+)""".r),
      RegexPattern(SigType.CLASS, """^class\s+(\w+)""".r),
      RegexPattern(SigType.TRAIT, """^trait\s+(\w+)""".r),
      RegexPattern(SigType.OBJECT, """^object\s+(\w+)""".r),
      RegexPattern(SigType.CASE_CLASS, """^case class\s+(\w+)""".r),
      RegexPattern(SigType.CASE_OBJECT, """^case object\s+(\w+)""".r),
      RegexPattern(SigType.DEF, """^def\s+(\w+)""".r),
      RegexPattern(SigType.VAL, """^val\s+(\w+)""".r),
      RegexPattern(SigType.VAR, """^var\s+(\w+)""".r),
      RegexPattern(SigType.TYPE, """^type\s+(\w+)""".r),
      RegexPattern(SigType.ENUM, """^enum\s+(\w+)""".r),
    )),
    Language("Java", Seq("java"), Seq.empty, Seq(
      RegexPattern(SigType.PACKAGE, """^package\s+([\w.]+)""".r),
      RegexPattern(SigType.IMPORT, """^import\s+([\w.]+)""".r),
      RegexPattern(SigType.CLASS, """^class\s+(\w+)""".r),
      RegexPattern(SigType.INTERFACE, """^interface\s+(\w+)""".r),
      RegexPattern(SigType.ENUM, """^enum\s+(\w+)""".r),
      RegexPattern(SigType.ANNOTATION, """^@interface\s+(\w+)""".r),
    )),
    Language("Python", Seq("py"), Seq("#!.*python"), Seq(
      RegexPattern(SigType.IMPORT, """^import\s+([\w.]+)""".r),
      RegexPattern(SigType.FROM_IMPORT, """^from\s+([\w.]+)\s+import""".r),
      RegexPattern(SigType.CLASS, """^class\s+(\w+)""".r),
      RegexPattern(SigType.DEF, """def\s+(\w+)""".r),
    )),
    Language("JavaScript", Seq("js", "jsx", "mjs", "cjs"), Seq("#!.*node"), Seq(
      RegexPattern(SigType.IMPORT, """^import\s+.*from\s+['\"]([^'\"]+)['\"]""".r),
      RegexPattern(SigType.EXPORT, """^export\s+(?:default\s+)?(class|function|const|let|var)\s+(\w+)""".r),
      RegexPattern(SigType.FUNCTION, """^function\s+(\w+)""".r),
      RegexPattern(SigType.CLASS, """^class\s+(\w+)""".r),
      RegexPattern(SigType.CONST, """^(?:export\s+)?(?:const|let|var)\s+(\w+)""".r),
    )),
    Language("TypeScript", Seq("ts", "tsx"), Seq.empty, Seq(
      RegexPattern(SigType.IMPORT, """^import\s+.*from\s+['\"]([^'\"]+)['\"]""".r),
      RegexPattern(SigType.EXPORT, """^export\s+(?:default\s+)?(class|interface|type|function|const|enum)\s+(\w+)""".r),
      RegexPattern(SigType.INTERFACE, """^interface\s+(\w+)""".r),
      RegexPattern(SigType.TYPE, """^type\s+(\w+)""".r),
      RegexPattern(SigType.CLASS, """^class\s+(\w+)""".r),
      RegexPattern(SigType.FUNCTION, """^function\s+(\w+)""".r),
      RegexPattern(SigType.ENUM, """^enum\s+(\w+)""".r),
    )),
    Language("Markdown", Seq("md", "mdx"), Seq.empty, Seq(
      RegexPattern(SigType.HEADING, """^(#{1,6})\s+(.*)""".r),
    )),
    Language("YAML", Seq("yaml", "yml"), Seq.empty, Seq(
      RegexPattern(SigType.TOP_KEY, """^(\w[\w-]*):""".r),
      RegexPattern(SigType.NESTED_KEY, """^\s{2,}(\w[\w-]*):""".r),
    )),
    Language("JSON", Seq("json"), Seq.empty, Seq(
      RegexPattern(SigType.TOP_KEY, """"(\w+)":\s*""".r),
    )),
    Language("C", Seq("c", "h"), Seq.empty, Seq(
      RegexPattern(SigType.INCLUDE, """^#include\s+[<\"]([^>\"]+)[>\"]""".r),
      RegexPattern(SigType.FUNCTION, """^\w+\s+\*?(\w+)\s*\(""".r),
      RegexPattern(SigType.STRUCT, """^struct\s+(\w+)""".r),
      RegexPattern(SigType.TYPEDEF, """^typedef\s+.*\s+(\w+);""".r),
    )),
    Language("C++", Seq("cpp", "cxx", "cc", "hpp", "hxx"), Seq.empty, Seq(
      RegexPattern(SigType.INCLUDE, """^#include\s+[<\"]([^>\"]+)[>\"]""".r),
      RegexPattern(SigType.CLASS, """^class\s+(\w+)""".r),
      RegexPattern(SigType.STRUCT, """^struct\s+(\w+)""".r),
      RegexPattern(SigType.NAMESPACE, """^namespace\s+(\w+)""".r),
    )),
    Language("Go", Seq("go"), Seq.empty, Seq(
      RegexPattern(SigType.PACKAGE, """^package\s+(\w+)""".r),
      RegexPattern(SigType.IMPORT, """^import\s+""".r),
      RegexPattern(SigType.FUNC, """^func\s+(\w+)""".r),
      RegexPattern(SigType.TYPE, """^type\s+(\w+)""".r),
      RegexPattern(SigType.STRUCT, """^type\s+(\w+)\s+struct""".r),
      RegexPattern(SigType.INTERFACE, """^type\s+(\w+)\s+interface""".r),
    )),
    Language("Rust", Seq("rs"), Seq.empty, Seq(
      RegexPattern(SigType.USE, """^use\s+([\w:]+)""".r),
      RegexPattern(SigType.FN, """^fn\s+(\w+)""".r),
      RegexPattern(SigType.STRUCT, """^struct\s+(\w+)""".r),
      RegexPattern(SigType.ENUM, """^enum\s+(\w+)""".r),
      RegexPattern(SigType.TRAIT, """^trait\s+(\w+)""".r),
      RegexPattern(SigType.IMPL, """^impl\s+(\w+)""".r),
      RegexPattern(SigType.TYPE, """^type\s+(\w+)""".r),
    )),
    Language("Ruby", Seq("rb"), Seq("#!.*ruby"), Seq(
      RegexPattern(SigType.REQUIRE, """^require\s+['\"]([^'\"]+)['\"]""".r),
      RegexPattern(SigType.MODULE, """^module\s+(\w+)""".r),
      RegexPattern(SigType.CLASS, """^class\s+(\w+)""".r),
      RegexPattern(SigType.DEF, """^def\s+(\w+)""".r),
    )),
    Language("Shell", Seq("sh", "bash", "zsh"), Seq("#!.*bash", "#!.*sh", "#!.*zsh"), Seq(
      RegexPattern(SigType.FUNCTION, """^(\w+)\s*\(\s*\)""".r),
      RegexPattern(SigType.VARIABLE, """^export\s+(\w+)=""".r),
    )),
  )
}
