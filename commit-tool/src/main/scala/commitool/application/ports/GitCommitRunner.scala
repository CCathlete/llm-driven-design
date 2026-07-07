package commitool.application.ports

import commitool.domain.models.CommitValidationError

trait GitCommitRunner {
  def commit(message: String): Either[CommitValidationError, Unit]
}
