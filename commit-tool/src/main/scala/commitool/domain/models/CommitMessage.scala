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
  private val HeaderRegex = "^(\\w+)\\(([^)]+)\\): (.+)$".r

  def fromRaw(raw: String): Either[CommitValidationError, CommitMessage] = {
    val lines = raw.split("\n").map(_.trim).filter(_.nonEmpty).toList
    if (lines.isEmpty) return Left(InvalidFormat(""))

    val headerLine = lines.head
    HeaderRegex.findFirstMatchIn(headerLine) match {
      case Some(m) =>
        val typeString = m.group(1)
        val scope = m.group(2)
        val description = m.group(3)

        if (scope.isEmpty) return Left(MissingScope(headerLine))
        if (description.isEmpty) return Left(InvalidFormat(headerLine))

        CommitType.fromString(typeString).flatMap {
          commitType =>
            val bodyStartIndex = raw.indexOf("\n\n")
            val bodyContent = if (bodyStartIndex != -1) {
              val fullBody = raw.substring(bodyStartIndex + 2).trim
              val bodyLines = fullBody.split("\n").toList
              val firstBlankLineIndex = bodyLines.indexOf("")
              if (firstBlankLineIndex != -1) {
                bodyLines.take(firstBlankLineIndex).mkString("\n").trim
              } else {
                fullBody
              }
            } else ""

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
