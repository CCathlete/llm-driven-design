package dtrbuilder.infrastructure.loader

import dtrbuilder.application.ports.SeedTemplateLoader
import scala.io.Source
import scala.util.Try

class ClasspathSeedTemplateLoader extends SeedTemplateLoader {

  private val SeedResourcePath: String = "seed/seed-dtr.dtr"

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
