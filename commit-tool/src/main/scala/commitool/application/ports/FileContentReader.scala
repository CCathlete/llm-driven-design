package commitool.application.ports

import commitool.domain.models.CommitValidationError

trait FileContentReader {
  def read(path: String): Either[CommitValidationError, String]
}
