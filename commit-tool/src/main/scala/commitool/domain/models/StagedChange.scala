package commitool.domain.models

sealed trait ChangeStatus {
  def label: String
}

object ChangeStatus {
  case object Added extends ChangeStatus { override val label: String = "new file:" }
  case object Modified extends ChangeStatus { override val label: String = "modified:" }
  case object Deleted extends ChangeStatus { override val label: String = "deleted:" }
}

case class StagedChange(status: ChangeStatus, filePath: String) {
  def changelistLabel: String = status.label
}
