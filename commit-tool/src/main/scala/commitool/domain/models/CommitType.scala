package commitool.domain.models

sealed abstract class CommitType {
  def label: String
}

object CommitType {
  case object Feat extends CommitType { override val label: String = "feat" }
  case object Fix extends CommitType { override val label: String = "fix" }
  case object Docs extends CommitType { override val label: String = "docs" }
  case object Style extends CommitType { override val label: String = "style" }
  case object Refactor extends CommitType { override val label: String = "refactor" }
  case object Perf extends CommitType { override val label: String = "perf" }
  case object Test extends CommitType { override val label: String = "test" }
  case object Build extends CommitType { override val label: String = "build" }
  case object Ci extends CommitType { override val label: String = "ci" }
  case object Chore extends CommitType { override val label: String = "chore" }
  case object Revert extends CommitType { override val label: String = "revert" }

  val allTypes: List[CommitType] = List(Feat, Fix, Docs, Style, Refactor, Perf, Test, Build, Ci, Chore, Revert)
  val validTypesString: String = allTypes.map(_.label).mkString("|")

  def fromString(s: String): Either[CommitValidationError, CommitType] = {
    allTypes.find(_.label == s) match {
      case Some(commitType) => Right(commitType)
      case None => Left(UnknownType(s, validTypesString))
    }
  }
}
