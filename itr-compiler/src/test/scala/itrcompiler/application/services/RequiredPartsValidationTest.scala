package itrcompiler.application.services

import itrcompiler.domain.models._
import org.scalatest.funspec.AnyFunSpec

class RequiredPartsValidationTest extends AnyFunSpec {
  val validator = new RequiredPartsValidation

  describe("RequiredPartsValidation") {
    describe("validateBatch") {
      it("should pass when ARCH and LEGEND are present via cu-type") {
        val batch = CUBatch(Seq(
          CU(id = "cu-001", dtrCoordinates = Seq.empty, content = "content 1", cuType = ArchCU),
          CU(id = "cu-002", dtrCoordinates = Seq.empty, content = "content 2", cuType = LegendCU)
        ))
        val result = validator.validateBatch(batch)
        assert(result.isValid)
        assert(result.missingParts.isEmpty)
      }

      it("should fail when ARCH is missing") {
        val batch = CUBatch(Seq(
          CU(id = "cu-002", dtrCoordinates = Seq.empty, content = "content 2", cuType = LegendCU)
        ))
        val result = validator.validateBatch(batch)
        assert(!result.isValid)
        assert(result.missingParts.contains(ArchCU))
      }

      it("should fail when all required parts are missing") {
        val batch = CUBatch(Seq(
          CU(id = "cu-001", dtrCoordinates = Seq.empty, content = "regular content")
        ))
        val result = validator.validateBatch(batch)
        assert(!result.isValid)
        assert(result.missingParts == CU.requiredParts)
        assert(result.existingParts.isEmpty)
      }

      it("should pass when required parts are inferred from ID") {
        val batch = CUBatch(Seq(
          CU(id = "arch", dtrCoordinates = Seq.empty, content = "arch content"),
          CU(id = "legend", dtrCoordinates = Seq.empty, content = "legend content")
        ))
        val result = validator.validateBatch(batch)
        assert(result.isValid)
      }

      it("should pass with extra optional CUs alongside required ones") {
        val batch = CUBatch(Seq(
          CU(id = "arch", dtrCoordinates = Seq.empty, content = "arch content"),
          CU(id = "legend", dtrCoordinates = Seq.empty, content = "legend content"),
          CU(id = "E2E-Verification", dtrCoordinates = Seq.empty, content = "e2e content"),
          CU(id = "my-verification", dtrCoordinates = Seq.empty, content = "verification content")
        ))
        val result = validator.validateBatch(batch)
        assert(result.isValid)
        assert(result.existingParts == Set(ArchCU, LegendCU))
      }

      it("should handle empty batch") {
        val batch = CUBatch(Seq.empty)
        val result = validator.validateBatch(batch)
        assert(!result.isValid)
        assert(result.missingParts == CU.requiredParts)
      }

      it("should handle mixed cu-type and ID-inferred parts") {
        val batch = CUBatch(Seq(
          CU(id = "arch-custom", dtrCoordinates = Seq.empty, content = "arch content", cuType = ArchCU),
          CU(id = "legend", dtrCoordinates = Seq.empty, content = "legend content")
        ))
        val result = validator.validateBatch(batch)
        assert(result.isValid)
      }

      it("should pass when ARCH and LEGEND exist on disk") {
        val tmpDir = java.nio.file.Files.createTempDirectory("test-validation-")
        java.nio.file.Files.write(tmpDir.resolve("ARCH.itr"), "# ARCH".getBytes)
        java.nio.file.Files.write(tmpDir.resolve("LEGEND.itr"), "# LEGEND".getBytes)
        val batch = CUBatch(Seq(
          CU(id = "cu-001", dtrCoordinates = Seq.empty, content = "regular content")
        ))
        val result = validator.validateBatch(batch, Some(tmpDir))
        assert(result.isValid)
        assert(result.existingParts == CU.requiredParts)
      }

      it("should pass when ARCH is in batch and LEGEND is on disk") {
        val tmpDir = java.nio.file.Files.createTempDirectory("test-validation-")
        java.nio.file.Files.write(tmpDir.resolve("LEGEND.itr"), "# LEGEND".getBytes)
        val batch = CUBatch(Seq(
          CU(id = "arch", dtrCoordinates = Seq.empty, content = "arch content"),
          CU(id = "cu-001", dtrCoordinates = Seq.empty, content = "regular content")
        ))
        val result = validator.validateBatch(batch, Some(tmpDir))
        assert(result.isValid)
      }

      it("should fail when neither batch nor disk has required parts") {
        val tmpDir = java.nio.file.Files.createTempDirectory("test-validation-")
        val batch = CUBatch(Seq(
          CU(id = "cu-001", dtrCoordinates = Seq.empty, content = "regular content")
        ))
        val result = validator.validateBatch(batch, Some(tmpDir))
        assert(!result.isValid)
        assert(result.missingParts == CU.requiredParts)
      }
    }

    describe("inferCuType (via validateBatch)") {
      it("should infer ArchCU from arch-prefixed ID") {
        val batch = CUBatch(Seq(CU(id = "arch-custom", dtrCoordinates = Seq.empty, content = "x")))
        val result = validator.validateBatch(batch)
        assert(result.existingParts.contains(ArchCU))
      }

      it("should infer LegendCU from legend-prefixed ID") {
        val batch = CUBatch(Seq(CU(id = "legend-custom", dtrCoordinates = Seq.empty, content = "x")))
        val result = validator.validateBatch(batch)
        assert(result.existingParts.contains(LegendCU))
      }

      it("should not infer special type for regular CU ID") {
        val batch = CUBatch(Seq(CU(id = "cu-001", dtrCoordinates = Seq.empty, content = "x")))
        val result = validator.validateBatch(batch)
        assert(result.existingParts.isEmpty)
      }
    }
  }
}
