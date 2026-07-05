package dtrbuilder.application.services

import dtrbuilder.domain.models.RawEntry
import dtrbuilder.application.ports.SeedTemplateLoader
import java.nio.file.Paths
import java.time.Instant

class BaselineDtrGenerator(seedTemplateLoader: SeedTemplateLoader) {

  def generate(
      rootPath: String,
      language: String = "Scala"
  ): Seq[RawEntry] = {
    val seedContent = seedTemplateLoader.loadSeed()
    val timestamp = Instant.now().toString

    val rootPathObj = Paths.get(rootPath)
    val appName = Option(rootPathObj.getFileName).fold("root")(_.toString)

    val substitutions = Map(
      "{{APP_NAME}}"     -> appName,
      "{{ROOT_PATH}}"    -> rootPath,
      "{{LANGUAGE}}"     -> language,
      "{{TIMESTAMP}}"    -> timestamp
    )

    val rendered = substitutions.foldLeft(seedContent) { case (content, (token, value)) =>
      content.replace(token, value)
    }

    rendered.linesIterator
      .map(_.stripTrailing)
      .filter { line =>
        line.nonEmpty && !line.startsWith("#")
      }
      .map(RawEntry.apply)
      .toSeq
  }
}
