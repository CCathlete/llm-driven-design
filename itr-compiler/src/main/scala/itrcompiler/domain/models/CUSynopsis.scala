package itrcompiler.domain.models

final case class CUSynopsis(
  features: Seq[String] = Seq.empty,
  maxAttempts: Int = 3,
  summary: String = ""
) extends Model {
  def isComplete: Boolean = features.nonEmpty && summary.trim.nonEmpty
}
