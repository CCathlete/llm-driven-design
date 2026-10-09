package itrcompiler.application.services

import itrcompiler.application.ports.DTRLoad
import itrcompiler.domain.models.{CompileCommand, CU, CUBatch, RegularCU}
import java.nio.file.Path

/** Service: Compile orchestration — the heart of the itr-compiler.
  *
  * Flow:
  *   1. Optionally load DTR content (provides coordinate targets)
  *   2. Determine input mode: raw (single CU), JSON batch, or YAML batch
  *   3. Validate required parts for batch mode (ARCH, LEGEND)
  *   4. For each CU: validate coordinates via CoordinateRules
  *   5. Build deterministic CU frames with header (CU-ID, timestamp, coords)
  *   6. Write each frame to the output folder via CUStore
  *
  * Error handling: per-CU continue-on-error with warnings.
  *
  * Note: Required parts validation only applies to batch mode (JSON/YAML).
  * Single CU mode (rawContent) is used for incremental additions and does
  * not enforce required parts.
  */
final class Compile(
    dtrLoad: DTRLoad,
    coordinateRules: CoordinateRules,
    cuStore: CUStore,
    contentDeserialize: ContentDeserialize,
    requiredPartsValidation: RequiredPartsValidation
) extends Service {

  /** Execute a compile command.
    *
    * @return the sequence of compiled CUs (partial output allowed on error)
    */
  def execute(cmd: CompileCommand): Seq[CU] = {
    val batch = buildBatch(cmd)
    val batchMode = cmd.jsonContent.isDefined || cmd.yamlContent.isDefined

    // Validate required parts (batch mode only)
    val validation =
      if (batchMode) requiredPartsValidation.validateBatch(batch, Some(cmd.outFolder))
      else
        // Single CU mode or empty: skip required parts validation
        requiredPartsValidation.ValidationResult(
          isValid = true,
          missingParts = Set.empty,
          existingParts = Set.empty
        )

    if (!validation.isValid) {
      throw new IllegalStateException(
        s"Missing required parts: ${validation.missingParts.mkString(", ")}. " +
        s"ITR must contain ARCH and LEGEND. They can be created incrementally."
      )
    }

    // Feat-1 enforcement: component + synopsis validation (cu-002).
    //
    // Enforcement applies to new-schema batches only (batch mode carrying a
    // synopsis and/or at least one component-carrying regular CU). A legacy
    // old-schema batch — no synopsis, every regular CU component-less — is
    // compiled unchanged for backward compatibility (spec criterion 11: "a
    // component-less legacy CU keeps exactly one <cu-id>.itr frame"; see also
    // cu-003/cu-004 regression). Single-CU raw mode never carries synopsis or
    // components, so it is exempt too.
    //
    // Override exemption: validation inspects ONLY CUs present in the batch.
    // Files already on disk in the output folder whose names match no batch
    // CU id (Advisor-placed overrides) are never validated, never deleted,
    // never overwritten unless `--force` targets their exact path.
    val isNewSchemaBatch =
      batch.synopsis.isDefined ||
        batch.cus.exists(cu => cu.cuType == RegularCU && cu.components.nonEmpty)
    if (batchMode && isNewSchemaBatch) {
      val feat1Errors =
        requiredPartsValidation.validateComponents(batch) ++
          requiredPartsValidation.validateSynopsis(batch)
      if (feat1Errors.nonEmpty) {
        throw new IllegalStateException(
          "ITR enforcement failed:\n" + feat1Errors.mkString("\n")
        )
      }
    }

    val validated = batch.cus.map { cu =>
      val coords = coordinateRules.validate(cu.dtrCoordinates)
      cu.copy(dtrCoordinates = coords)
    }
    validated.foreach { cu =>
      try {
        cuStore.store(cu, cmd.outFolder, cmd.force)
      } catch {
        case e: Exception =>
          System.err.println(s"Warning: failed to write CU '${cu.id}': ${e.getMessage}")
      }
    }
    batch.synopsis.foreach { syn =>
      try {
        cuStore.storeSynopsis(cmd.outFolder, syn, validated, cmd.force)
      } catch {
        case e: Exception =>
          System.err.println(s"Warning: failed to write SYNOPSIS.itr: ${e.getMessage}")
      }
    }
    validated
  }

  /** Determine input mode and build the CUBatch. */
  private def buildBatch(cmd: CompileCommand): CUBatch =
    (cmd.rawContent, cmd.jsonContent, cmd.yamlContent) match {
      case (Some(raw), _, _) =>
        val coords = loadCoordinates(cmd.dtr)
        CUBatch(Seq(
          CU(
            id = cmd.cuId.getOrElse("unknown"),
            dtrCoordinates = coords,
            content = raw
          )
        ))

      case (_, Some(jsonPath), _) =>
        contentDeserialize.fromJson(jsonPath)

      case (_, _, Some(yamlPath)) =>
        contentDeserialize.fromYaml(yamlPath)

      case _ =>
        System.err.println("Warning: no input content provided (use --raw-content, --json-content, or --yaml-content)")
        CUBatch(Seq.empty)
    }

  /** Optionally load DTR content and return its lines as coordinates. */
  private def loadCoordinates(dtr: Option[Path]): Seq[String] =
    dtr.map(p => dtrLoad.load(p).split("\n").toSeq).getOrElse(Seq.empty)
}
