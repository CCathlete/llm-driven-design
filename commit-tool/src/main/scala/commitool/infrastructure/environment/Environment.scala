package commitool.infrastructure.environment

object Environment {
  private lazy val loadedEnv: Map[String, String] = DotEnvLoader.load()

  def get(key: String): Option[String] = loadedEnv.get(key)
}
