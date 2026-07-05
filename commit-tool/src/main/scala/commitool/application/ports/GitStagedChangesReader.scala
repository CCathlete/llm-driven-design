package commitool.application.ports

import commitool.domain.models.{CommitValidationError, StagedChange}

trait GitStagedChangesReader {
  def readStagedChanges(): Either[CommitValidationError, List[StagedChange]]
}
