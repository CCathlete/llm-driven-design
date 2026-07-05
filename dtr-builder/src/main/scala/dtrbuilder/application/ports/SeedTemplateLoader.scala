package dtrbuilder.application.ports

/** Port: loads the seed DTR template from a classpath resource.
  *
  * The seed template is a canonical DTR file with placeholder tokens
  * ({{APP_NAME}}, {{PACKAGE_NAME}}, {{ROOT_PATH}}, etc.) that the
  * BaselineDtrGenerator substitutes with user-provided values.
  */
trait SeedTemplateLoader {

  /** Load the seed template content as a raw string.
    *
    * @return the full text of the seed-dtr.dtr resource
    * @throws RuntimeException if the seed resource is not found on the classpath
    */
  def loadSeed(): String
}
