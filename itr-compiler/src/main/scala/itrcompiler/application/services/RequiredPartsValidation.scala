package itrcompiler.application.services

import itrcompiler.domain.models.{CU, CUBatch, CUType, RegularCU, ArchCU, LegendCU, VerificationCU, E2eVerificationCU}

import java.nio.file.{Files, Path}

/** Service: validates that an ITR output folder contains all required parts
  * before allowing compilation to proceed.
  *
  * Required parts: ARCH, LEGEND.
  *
  * Detection: cu-type field first, then fallback to ID matching.
  */
final class RequiredPartsValidation extends Service {

  /** Scan an output folder on disk and return the set of required CU types
    * whose corresponding ITR files already exist.
    */
  private def checkDiskParts(folder: java.nio.file.Path): Set[CUType] = {
    import java.nio.file.Files
    if (!Files.exists(folder)) return Set.empty
    val existing = Set.newBuilder[CUType]
    if (Files.exists(folder.resolve("ARCH.itr"))) existing += ArchCU
    if (Files.exists(folder.resolve("LEGEND.itr"))) existing += LegendCU
    existing.result()
  }

  case class ValidationResult(
    isValid: Boolean,
    missingParts: Set[CUType],
    existingParts: Set[CUType]
  )

  /** Infer CUType from ID when cu-type is not specified (backward compat). */
  private def inferCuType(cu: CU): CUType = {
    if (cu.cuType != RegularCU) return cu.cuType
    val id = cu.id.toLowerCase
    // E2E check must come before general verification check
    if (id == "arch" || id.startsWith("arch-")) ArchCU
    else if (id == "legend" || id.startsWith("legend-")) LegendCU
    else if (id == "e2everification" || id == "e2e-verification" || id.startsWith("e2everification-") || id.contains("e2e") && id.contains("verification")) E2eVerificationCU
    else if (id.contains("verification")) VerificationCU
    else RegularCU
  }

  def validateBatch(batch: CUBatch, outFolder: Option[java.nio.file.Path] = None): ValidationResult = {
    val batchTypes = batch.cus.map(inferCuType).toSet
    val diskParts = outFolder.map(checkDiskParts).getOrElse(Set.empty)
    val allParts = batchTypes ++ diskParts
    val missing = CU.requiredParts -- allParts
    ValidationResult(
      isValid = missing.isEmpty,
      missingParts = missing,
      existingParts = allParts.intersect(CU.requiredParts)
    )
  }

  def validateForSingleCU(
    outFolder: Path,
    currentCU: CU,
    existingFiles: Set[String]
  ): ValidationResult = {
    val inferredType = inferCuType(currentCU)
    val willCreate = if (inferredType != RegularCU) Set(inferredType) else Set.empty
    val existing = CU.requiredParts.filter { part =>
      part match {
        case ArchCU => existingFiles.contains("ARCH.itr")
        case LegendCU => existingFiles.contains("LEGEND.itr")
        case VerificationCU => existingFiles.exists(_.endsWith(".verification.itr"))
        case E2eVerificationCU => existingFiles.contains("E2EVERIFICATION.itr")
        case _ => false
      }
    }
    val covered = existing ++ willCreate
    val missing = CU.requiredParts -- covered
    ValidationResult(
      isValid = missing.isEmpty,
      missingParts = missing,
      existingParts = existing
    )
  }

  /** Report component omissions for regular CUs that opt into the
    * component schema. A fully component-less regular CU is a legacy
    * old-schema CU: it is exempt and keeps its single per-CU frame
    * (feature spec criterion 11). Only CUs that already carry at least one
    * component are required to carry all four.
    */
  def validateComponents(batch: CUBatch): Seq[String] =
    batch.cus.filter(cu => cu.cuType == RegularCU && cu.components.nonEmpty).flatMap { cu =>
      cu.missingComponents.toSeq.sorted.map(name => s"CU '${cu.id}' is missing component '$name'")
    }

  def validateSynopsis(batch: CUBatch): Seq[String] = batch.synopsis match {
    case None => Seq("SYNOPSIS is missing: batch carries no synopsis data (features, MAX_ATTEMPTS, implementation summary)")
    case Some(s) if !s.isComplete => Seq("SYNOPSIS is incomplete: implementation summary is missing or empty")
    case _ => Seq.empty
  }
}
