package itrcompiler.application.services

import itrcompiler.application.ports.DTRLoad
import itrcompiler.domain.models.{CompileCommand, CU, CUBatch}
import java.nio.file.Path

/** Service: Compile orchestration — the heart of the itr-compiler.
  *
  * Flow:
  *   1. Optionally load DTR content (provides coordinate targets)
  *   2. Determine input mode: raw (single CU), JSON batch, or YAML batch
  *   3. Validate required parts for batch mode (ARCH, LEGEND, VERIFICATION, E2EVERIFICATION)
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

    // Validate required parts (batch mode only)
    val validation = cmd match {
      case _ if cmd.jsonContent.isDefined || cmd.yamlContent.isDefined =>
        requiredPartsValidation.validateBatch(batch)
      case _ =>
        // Single CU mode or empty: skip required parts validation
        requiredPartsValidation.ValidationResult(
          isValid = true,
          missingParts = Set.empty,
          existingParts = Set.empty
        )
    }

    if (!validation.isValid) {
      throw new IllegalStateException(
        s"Missing required parts: ${validation.missingParts.mkString(", ")}. " +
        s"ITR must contain ARCH, LEGEND, VERIFICATION, and E2EVERIFICATION."
      )
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
