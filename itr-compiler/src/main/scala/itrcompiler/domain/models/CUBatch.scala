package itrcompiler.domain.models

/** A batch of CUs, typically deserialized from a JSON or YAML input source. */
final case class CUBatch(
    cus: Seq[CU]
) extends Model
