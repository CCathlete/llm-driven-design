package dtrbuilder.infrastructure

import dtrbuilder.application.SeedTemplateLoader
import scala.io.Source
import scala.util.Try

/** Infrastructure adapter: loads the seed DTR template from classpath resource.
  *
  * The seed resource is located at "seed/seed-dtr.dtr" on the classpath.
  * If the resource cannot be found, a hard error is thrown with a clear message.
  */
class ClasspathSeedTemplateLoader extends SeedTemplateLoader {

  private val SeedResourcePath: String = "seed/seed-dtr.dtr"

  /** Load the seed template content from the classpath resource.
    *
    * @return the full text of the seed-dtr.dtr resource
    * @throws RuntimeException if the seed resource is not found or cannot be read
    */
  override def loadSeed(): String = {
    val resourceOpt = Option(getClass.getClassLoader.getResource(SeedResourcePath))

    resourceOpt match {
      case Some(resource) =>
        Try {
          val source = Source.createBufferedSource(resource.openStream())(scala.io.Codec.UTF8)
          try source.mkString
          finally source.close()
        }.getOrElse {
          throw new RuntimeException(
            s"Failed to read seed template from classpath resource '$SeedResourcePath'"
          )
        }

      case None =>
        throw new RuntimeException(
          s"Seed template not found on classpath at '$SeedResourcePath'. " +
          s"Ensure seed/seed-dtr.dtr is in src/main/resources/."
        )
    }
  }
}
