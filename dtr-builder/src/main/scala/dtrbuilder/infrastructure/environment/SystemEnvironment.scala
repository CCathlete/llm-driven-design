package dtrbuilder.infrastructure.environment

import dtrbuilder.application.ports.Environment
import scala.collection.concurrent.TrieMap

object SystemEnvironment extends Environment {

  private val overrides: TrieMap[String, String] = TrieMap.empty

  override def get(key: String): Option[String] = {
    overrides.get(key).orElse(sys.env.get(key))
  }

  override def set(key: String, value: String): Unit = {
    overrides.put(key, value)
  }

  override def toMap: Map[String, String] = {
    sys.env ++ overrides
  }

  override def overrideKeys: Set[String] = overrides.keySet.toSet

  def clear(): Unit = overrides.clear()
}
