package itrcompiler.application.services

import itrcompiler.domain.models.{CU, CUBatch, CUType, RegularCU, ArchCU, LegendCU, VerificationCU, E2eVerificationCU}

import java.nio.file.Path

/** Service: validates that an ITR output folder contains all required parts
  * before allowing compilation to proceed.
  *
  * Required parts: ARCH, LEGEND, VERIFICATION, E2EVERIFICATION.
  *
  * Detection: cu-type field first, then fallback to ID matching.
  */
final class RequiredPartsValidation extends Service {

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
}
