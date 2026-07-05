package dtrbuilder.domain.models

/** Raw signature data before relation inference. */
sealed trait Signature

object Signature {
  /** A signature identified by its raw text. */
  final case class Text(value: String) extends Signature

  /** A signature identified by its semantic type. */
  final case class Typed(sigType: SigType, text: String) extends Signature
}
