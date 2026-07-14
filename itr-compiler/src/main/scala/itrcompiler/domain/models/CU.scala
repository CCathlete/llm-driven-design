package itrcompiler.domain.models

import java.time.Instant

/** A Computational Unit — the fundamental work item in an ITR.
  *
  * Each CU carries an identifier, an optional list of DTR coordinates
  * that target addresses in the DTR, the free-form content payload,
  * and an auto-generated timestamp.
  */

sealed trait CUType
case object RegularCU extends CUType
case object ArchCU extends CUType
case object LegendCU extends CUType
case object VerificationCU extends CUType
case object E2eVerificationCU extends CUType

object CUType {
  def fromString(s: String): CUType = s.toLowerCase match {
    case "arch" | "archcu" => ArchCU
    case "legend" | "legendcu" => LegendCU
    case "verification" | "verificationcu" => VerificationCU
    case "e2everification" | "e2everificationcu" => E2eVerificationCU
    case _ => RegularCU
  }
}

final case class CU(
  id: String,
  dtrCoordinates: Seq[String],
  content: String,
  cuType: CUType = RegularCU,
  timestamp: Instant = Instant.now()
) extends Model {
  def isRequiredPart: Boolean = cuType match {
    case ArchCU | LegendCU | VerificationCU | E2eVerificationCU => true
    case RegularCU => false
  }

  def fileName: String = cuType match {
    case ArchCU => "ARCH.itr"
    case LegendCU => "LEGEND.itr"
    case VerificationCU => s"${id}.verification.itr"
    case E2eVerificationCU => "E2EVERIFICATION.itr"
    case RegularCU => s"${id}.itr"
  }
}

object CU {
  val requiredParts: Set[CUType] = Set(
    ArchCU, LegendCU, VerificationCU, E2eVerificationCU
  )
}
