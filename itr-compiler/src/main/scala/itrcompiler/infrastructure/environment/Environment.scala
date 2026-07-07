package itrcompiler.infrastructure.environment

import itrcompiler.application.ports.Port

/** Environment singleton — provides access to system environment and config.
  *
  * Implements the Port interface as required by the hexagonal architecture.
  * Merges system env vars with loaded .env values (system takes precedence).
  */
object Environment extends Port {
  private var overrides: Map[String, String] = Map.empty

  def get(key: String): Option[String] =
    overrides.get(key).orElse(sys.env.get(key))

  def set(key: String, value: String): Unit =
    overrides += key -> value

  def toMap: Map[String, String] =
    sys.env ++ overrides

  def overrideKeys: Set[String] =
    overrides.keySet
}
