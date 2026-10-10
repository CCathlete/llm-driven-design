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

  /** Normalise a component key to the lower-case hyphenated form used by
    * `CU.requiredComponents` (underscore/dash/case insensitive).
    */
  private def normaliseKey(key: String): String = key.toLowerCase.replace("_", "-")

  /** Effective component keys: exactly what the writer will emit for this
    * CU. `FileSystem.write` branches on `cu.components.nonEmpty` and writes
    * one file per key in that map, so validation reads the SAME map —
    * enforcement and the written layout cannot disagree.
    *
    * Split-derived keys are covered because cu-002's JSON splitter
    * (JSONFormat.splitInlineSections) already populates `cu.components`
    * from inline content sections, and explicit `"components"` objects are
    * populated by cu-001's key-order-independent parser. Keys are
    * normalised to lower-case with `_` treated as `-`.
    */
  def effectiveComponentKeys(cu: CU): Set[String] =
    cu.components.keySet.map(normaliseKey)

  /** Component keys supplied by an explicit `"components"` object rather
    * than derived by cu-002's content split: keys the content itself does
    * not carry as labeled section headers (`CU.contentSectionKeys` shares
    * the splitter's label list). A batch opting into the feat-1 component
    * schema explicitly must also carry synopsis data (feat-1 criteria
    * 9-10); a plain Advisor-drafted batch whose sections live only in
    * `content` and which carries no synopsis at all is the bug-2 repro /
    * feat-1 batch.json shape and is exempt from synopsis enforcement.
    */
  def explicitComponentKeys(cu: CU): Set[String] =
    effectiveComponentKeys(cu) -- CU.contentSectionKeys(cu.content)

  /** Report component omissions for new-schema batches.
    *
    * Legacy exemption (spec criterion 11): a batch with no synopsis and
    * zero component-carrying regular CUs (explicit or split-derived — both
    * land in `cu.components`) is a genuinely component-less legacy batch —
    * it is exempt and keeps its single per-CU frame. Every other batch is
    * new-schema: EVERY regular CU must carry all four sections, so even a
    * fully component-less regular CU in a new-schema batch is reported
    * with all four missing names.
    *
    * A CU whose content merely mentions component filenames or words
    * mid-prose carries no components (nothing will be written for it), so
    * it is reported missing rather than silently passed — validation and
    * the writer read the same `cu.components` map (wave-1 watch item 2).
    */
  def validateComponents(batch: CUBatch): Seq[String] = {
    val carriesComponents = batch.cus.exists(cu =>
      cu.cuType == RegularCU && effectiveComponentKeys(cu).nonEmpty
    )
    if (batch.synopsis.isEmpty && !carriesComponents) return Seq.empty
    batch.cus.filter(_.cuType == RegularCU).flatMap { cu =>
      val missing = (CU.requiredComponents -- effectiveComponentKeys(cu)).toSeq.sorted
      missing.map(name => s"CU '${cu.id}' is missing component '$name'")
    }
  }

  /** Synopsis enforcement (feat-1 criteria 9-10).
    *
    * - Synopsis data present: validate its content (features,
    *   MAX_ATTEMPTS, implementation summary).
    * - Synopsis absent: only a batch that opted into the component schema
    *   through an explicit `"components"` object fails (it asked for
    *   SYNOPSIS.itr enforcement without providing synopsis data). A batch
    *   whose components were split out of inline content sections and that
    *   carries no synopsis at all is exempt, so the bug-2 minimal repro
    *   and feat-1's own `batch.json` keep compiling (feature tests T2/T3).
    * - Legacy component-less batch: exempt.
    */
  def validateSynopsis(batch: CUBatch): Seq[String] = batch.synopsis match {
    case None =>
      val explicitComponents = batch.cus.exists(cu =>
        cu.cuType == RegularCU && explicitComponentKeys(cu).nonEmpty
      )
      if (explicitComponents)
        Seq("SYNOPSIS is missing: batch carries no synopsis data (features, MAX_ATTEMPTS, implementation summary)")
      else Seq.empty
    case Some(s) if !s.isComplete => Seq("SYNOPSIS is incomplete: implementation summary is missing or empty")
    case _ => Seq.empty
  }
}
