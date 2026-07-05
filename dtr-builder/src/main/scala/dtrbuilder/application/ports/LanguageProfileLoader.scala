package dtrbuilder.application.ports

import dtrbuilder.domain.models.Language

/** Port: loads language profiles. */
trait LanguageProfileLoader {
  def loadAll(): Seq[Language]
}
