package commitool.domain.models

case class CommitMessage(commitType: CommitType, scope: String, description: String, body: String) {
  def compose(changelist: Changelist): String = {
    val header = s"${commitType.label}($scope): $description"
    val changelistString = changelist.formatAsString

    if (changelistString.nonEmpty) {
      s"$header\n\n$body\n\n$changelistString"
    } else {
      s"$header\n\n$body"
    }
  }
}

object CommitMessage {
  private val HeaderRegex = "^(\\w+)\\(([^)]*)\\): (.+)$".r

  def fromRaw(raw: String): Either[CommitValidationError, CommitMessage] = {
    val allLines = raw.split("\n", -1).toList
    val headerIndex = allLines.indexWhere(_.trim.nonEmpty)
    if (headerIndex == -1) return Left(InvalidFormat(""))

    val headerLine = allLines(headerIndex).trim
    HeaderRegex.findFirstMatchIn(headerLine) match {
      case Some(m) =>
        val typeString = m.group(1)
        val scope = m.group(2)
        val description = m.group(3)

        if (scope.isEmpty) return Left(MissingScope(headerLine))
        if (description.isEmpty) return Left(InvalidFormat(headerLine))

        CommitType.fromString(typeString).flatMap { commitType =>
          val afterHeaderLines = allLines.drop(headerIndex + 1)
          val bodyStartIndex = afterHeaderLines.indexWhere(_.trim.isEmpty)
          val bodyContent = if (bodyStartIndex != -1) {
            afterHeaderLines.drop(bodyStartIndex + 1).mkString("\n").trim
          } else {
            afterHeaderLines.mkString("\n").trim
          }

          if (bodyContent.isEmpty) {
            Left(EmptyBody)
          } else {
            Right(CommitMessage(commitType, scope, description, bodyContent))
          }
        }
      case None => Left(InvalidFormat(headerLine))
    }
  }
}
