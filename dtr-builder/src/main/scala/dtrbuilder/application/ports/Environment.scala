package dtrbuilder.application.ports

/** Environment abstraction: a mutable key-value store that merges system env vars
  * with loaded dotenv vars. System env takes precedence on conflict.
  *
  * This is defined in the application layer as a port so that DtrBuilderService
  * does not depend on a concrete infrastructure singleton.
  */
trait Environment {
  /** Get a value by key. Checks overrides first, then system env. */
  def get(key: String): Option[String]

  /** Set an override value (from dotenv or CLI). Does not affect system env. */
  def set(key: String, value: String): Unit

  /** Get all values as a flat map (overrides + system env, system wins). */
  def toMap: Map[String, String]

  /** Get all override keys that were explicitly set. */
  def overrideKeys: Set[String]
}
