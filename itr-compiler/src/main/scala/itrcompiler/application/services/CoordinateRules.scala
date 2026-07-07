package itrcompiler.application.services

/** Service: validates and normalizes DTR coordinate strings.
  *
  * Coordinates must be non-blank and trimmed. Invalid or blank
  * coordinates are filtered out. This runs per-CU during compile.
  */
final class CoordinateRules extends Service {

  /** Validate and normalize a list of DTR coordinate strings.
    * Blank entries are removed; each entry is trimmed.
    */
  def validate(coordinates: Seq[String]): Seq[String] = {
    coordinates
      .map(_.trim)
      .filterNot(_.isEmpty)
  }
}
