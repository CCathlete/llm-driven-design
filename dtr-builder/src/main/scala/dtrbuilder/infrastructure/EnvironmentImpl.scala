package dtrbuilder.infrastructure

import dtrbuilder.application.Environment
import scala.collection.concurrent.TrieMap

/** Infrastructure singleton: merges system environment variables with loaded dotenv vars.
  * System env takes precedence on conflict. Thread-safe using TrieMap.
  */
object EnvironmentImpl extends Environment {

  private val overrides: TrieMap[String, String] = TrieMap.empty

  /** Get a value: check overrides first, then system env. */
  override def get(key: String): Option[String] = {
    overrides.get(key).orElse(sys.env.get(key))
  }

  /** Set an override value. Does not affect system env. */
  override def set(key: String, value: String): Unit = {
    overrides.put(key, value)
  }

  /** Get all values: overrides merged with system env. System wins on conflict. */
  override def toMap: Map[String, String] = {
    sys.env ++ overrides  // overrides take precedence (sys.env ++ overrides means overrides win)
  }

  /** Get all override keys. */
  override def overrideKeys: Set[String] = overrides.keySet.toSet

  /** Clear all overrides (useful for testing). */
  def clear(): Unit = overrides.clear()
}
