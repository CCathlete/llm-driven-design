package commitool.domain.models

case class Changelist(changes: List[StagedChange]) {
  def formatAsString: String = {
    if (changes.isEmpty) {
      ""
    } else {
      val header = "Changes to be committed:"
      val formattedChanges = changes.map(change => s"  ${change.changelistLabel}\t${change.filePath}").mkString("\n")
      s"$header\n$formattedChanges"
    }
  }
}
