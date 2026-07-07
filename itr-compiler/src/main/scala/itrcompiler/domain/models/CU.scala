package itrcompiler.domain.models

import java.time.Instant

/** A Computational Unit — the fundamental work item in an ITR.
  *
  * Each CU carries an identifier, an optional list of DTR coordinates
  * that target addresses in the DTR, the free-form content payload,
  * and an auto-generated timestamp.
  */
final case class CU(
    id: String,
    dtrCoordinates: Seq[String],
    content: String,
    timestamp: Instant = Instant.now()
) extends Model
