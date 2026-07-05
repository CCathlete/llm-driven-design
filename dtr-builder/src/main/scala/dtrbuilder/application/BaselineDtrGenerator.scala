package dtrbuilder.application

import dtrbuilder.domain.RawEntry
import java.time.Instant

/** Application service: generates a baseline DTR by mutating the seed template
  * with user-provided parameters.
  *
  * The seed template contains placeholder tokens like {{APP_NAME}},
  * {{ROOT_PATH}}, {{LANGUAGE}}, {{TIMESTAMP}}. Each occurrence is replaced
  * with the corresponding user-provided value.
  *
  * The output is a sequence of RawEntry lines suitable for writing via DtrWriter.
  */
class BaselineDtrGenerator(seedTemplateLoader: SeedTemplateLoader) {

  /** Generate a baseline DTR by loading the seed template and substituting parameters.
    *
    * @param appName     the application name (e.g. "my-app")
    * @param rootPath    the project root path (e.g. "/home/user/projects/my-app")
    * @param language    the primary language (default "Scala")
    * @return            sequence of RawEntry lines forming the baseline DTR
    */
  def generate(
      appName: String,
      rootPath: String,
      language: String = "Scala"
  ): Seq[RawEntry] = {
    val seedContent = seedTemplateLoader.loadSeed()
    val timestamp = Instant.now().toString

    val substitutions = Map(
      "{{APP_NAME}}"     -> appName,
      "{{ROOT_PATH}}"    -> rootPath,
      "{{LANGUAGE}}"     -> language,
      "{{TIMESTAMP}}"    -> timestamp
    )

    val rendered = substitutions.foldLeft(seedContent) { case (content, (token, value)) =>
      content.replace(token, value)
    }

    // Split into lines, filter out comments and blank lines, produce RawEntry
    rendered.linesIterator
      .map(_.stripTrailing)
      .filter { line =>
        line.nonEmpty && !line.startsWith("#")
      }
      .map(RawEntry.apply)
      .toSeq
  }
}
