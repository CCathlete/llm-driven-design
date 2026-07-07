package commitool.domain.models

sealed trait CommitValidationError {
  def message: String
}

case class InvalidFormat(line: String) extends CommitValidationError {
  override def message: String = s"Commit message header has an invalid format: '$line'. Expected format: 'type(scope): description'."
}

case class UnknownType(got: String, valid: String) extends CommitValidationError {
  override def message: String = s"Unknown commit type: '$got'. Valid types are: $valid."
}

case class MissingScope(line: String) extends CommitValidationError {
  override def message: String = s"Commit message header is missing a scope: '$line'. Expected format: 'type(scope): description'."
}

case object EmptyBody extends CommitValidationError {
  override def message: String = "Commit message body cannot be empty."
}

case object NoStagedChanges extends CommitValidationError {
  override def message: String = "No changes are staged for commit. Please stage your changes before committing."
}

case class FileNotFound(path: String) extends CommitValidationError {
  override def message: String = s"The specified message file was not found: '$path'."
}

case class MessageFileEmpty(path: String) extends CommitValidationError {
  override def message: String = s"The message file at '$path' is empty. Please provide a non-empty commit message."
}

case class GitError(stderr: String) extends CommitValidationError {
  override def message: String = s"Git command failed with the following error:\n$stderr"
}

case class ReadError(path: String, detail: String) extends CommitValidationError {
  override def message: String = s"Error reading file '$path': $detail"
}
