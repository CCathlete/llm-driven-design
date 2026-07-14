package itrcompiler.application.services

import itrcompiler.domain.models.{CU, CUBatch, CUType, ArchCU, LegendCU, VerificationCU, E2eVerificationCU}

import java.nio.file.Path

/** Service: validates that an ITR output folder contains all required parts
  * before allowing compilation to proceed.
  *
  * Required parts: ARCH, LEGEND, VERIFICATION, E2EVERIFICATION.
  */
final class RequiredPartsValidation {

  case class ValidationResult(
    isValid: Boolean,
    missingParts: Set[CUType],
    existingParts: Set[CUType]
  )

  def validateBatch(batch: CUBatch): ValidationResult = {
    val batchTypes = batch.cus.map(_.cuType).toSet
    val missing = CU.requiredParts -- batchTypes
    ValidationResult(
      isValid = missing.isEmpty,
      missingParts = missing,
      existingParts = batchTypes.intersect(CU.requiredParts)
    )
  }

  def validateForSingleCU(
    outFolder: Path,
    currentCU: CU,
    existingFiles: Set[String]
  ): ValidationResult = {
    val willCreate = if (currentCU.isRequiredPart) Set(currentCU.cuType) else Set.empty
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
