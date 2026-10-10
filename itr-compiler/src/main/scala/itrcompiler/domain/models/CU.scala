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
    case "e2everification" | "e2everificationcu" | "e2e-verification" | "e2e-verificationcu" => E2eVerificationCU
    case _ => RegularCU
  }
}

final case class CU(
  id: String,
  dtrCoordinates: Seq[String],
  content: String,
  cuType: CUType = RegularCU,
  timestamp: Instant = Instant.now(),
  components: Map[String, String] = Map.empty
) extends Model {
  def isRequiredPart: Boolean = cuType match {
    case ArchCU | LegendCU => true
    case _ => false
  }

  def fileName: String = cuType match {
    case ArchCU => "ARCH.itr"
    case LegendCU => "LEGEND.itr"
    case VerificationCU => s"${id}.verification.itr"
    case E2eVerificationCU => "E2EVERIFICATION.itr"
    case RegularCU => s"${id}.itr"
  }

  def missingComponents: Set[String] = CU.requiredComponents -- components.keySet
}

object CU {
  val requiredParts: Set[CUType] = Set(
    ArchCU, LegendCU
  )

  val requiredComponents: Set[String] = Set(
    "coordinates", "requirements", "implementation-steps", "acceptance"
  )

  val componentFileNames: Map[String, String] = Map(
    "coordinates" -> "COORDINATES.itr",
    "requirements" -> "REQUIREMENTS.itr",
    "implementation-steps" -> "IMPLEMENTATION_STEPS.itr",
    "acceptance" -> "ACCEPTANCE.itr"
  )

  /** Labeled section headers of the compile-itr skill's CU content bar, as
    * (label, component-key) pairs: the four labels a regular CU carries
    * inline in its `content` string. Single definition shared by cu-002's
    * JSON splitter (JSONFormat.splitInlineSections) and by validation, so
    * "what counts as a section" cannot drift between parser and
    * enforcement (wave-1 watch item: splitter/detector agreement).
    */
  val sectionLabels: Seq[(String, String)] = Seq(
    "REQUIREMENTS:" -> "requirements",
    "COORDINATES:" -> "coordinates",
    "IMPLEMENTATION_STEPS:" -> "implementation-steps",
    "ACCEPTANCE:" -> "acceptance"
  )

  /** Component keys whose labeled section header occurs in `content` —
    * the same scan the JSON splitter performs before populating
    * `CU.components`. Used by validation to tell a component key derived
    * from cu-002's split apart from one supplied by an explicit
    * "components" object.
    */
  def contentSectionKeys(content: String): Set[String] =
    if (content == null || content.isEmpty) Set.empty
    else sectionLabels.collect { case (label, key) if content.contains(label) => key }.toSet
}
