package itrcompiler.application.services

import itrcompiler.domain.models._
import org.scalatest.funspec.AnyFunSpec

class RequiredPartsValidationTest extends AnyFunSpec {
  val validator = new RequiredPartsValidation

  describe("RequiredPartsValidation") {
    describe("validateBatch") {
      it("should pass when all required parts are present via cu-type") {
        val batch = CUBatch(Seq(
          CU(id = "cu-001", dtrCoordinates = Seq.empty, content = "content 1", cuType = ArchCU),
          CU(id = "cu-002", dtrCoordinates = Seq.empty, content = "content 2", cuType = LegendCU),
          CU(id = "cu-003", dtrCoordinates = Seq.empty, content = "content 3", cuType = VerificationCU),
          CU(id = "cu-004", dtrCoordinates = Seq.empty, content = "content 4", cuType = E2eVerificationCU)
        ))
        val result = validator.validateBatch(batch)
        assert(result.isValid)
        assert(result.missingParts.isEmpty)
        assert(result.existingParts == CU.requiredParts)
      }

      it("should fail when ARCH is missing") {
        val batch = CUBatch(Seq(
          CU(id = "cu-002", dtrCoordinates = Seq.empty, content = "content 2", cuType = LegendCU),
          CU(id = "cu-003", dtrCoordinates = Seq.empty, content = "content 3", cuType = VerificationCU),
          CU(id = "cu-004", dtrCoordinates = Seq.empty, content = "content 4", cuType = E2eVerificationCU)
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
          CU(id = "legend", dtrCoordinates = Seq.empty, content = "legend content"),
          CU(id = "verification", dtrCoordinates = Seq.empty, content = "verification content"),
          CU(id = "e2e-verification", dtrCoordinates = Seq.empty, content = "e2e content")
        ))
        val result = validator.validateBatch(batch)
        assert(result.isValid)
      }

      it("should infer E2eVerificationCU from hyphenated ID") {
        val batch = CUBatch(Seq(
          CU(id = "arch", dtrCoordinates = Seq.empty, content = "arch content"),
          CU(id = "legend", dtrCoordinates = Seq.empty, content = "legend content"),
          CU(id = "verification", dtrCoordinates = Seq.empty, content = "verification content"),
          CU(id = "E2E-Verification", dtrCoordinates = Seq.empty, content = "e2e content")
        ))
        val result = validator.validateBatch(batch)
        assert(result.isValid)
        assert(result.existingParts.contains(E2eVerificationCU))
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
          CU(id = "legend", dtrCoordinates = Seq.empty, content = "legend content"),
          CU(id = "my-verification-test", dtrCoordinates = Seq.empty, content = "verification content"),
          CU(id = "e2everification", dtrCoordinates = Seq.empty, content = "e2e content")
        ))
        val result = validator.validateBatch(batch)
        assert(result.isValid)
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

      it("should infer VerificationCU from verification-containing ID") {
        val batch = CUBatch(Seq(CU(id = "my-verification", dtrCoordinates = Seq.empty, content = "x")))
        val result = validator.validateBatch(batch)
        assert(result.existingParts.contains(VerificationCU))
      }

      it("should not infer special type for regular CU ID") {
        val batch = CUBatch(Seq(CU(id = "cu-001", dtrCoordinates = Seq.empty, content = "x")))
        val result = validator.validateBatch(batch)
        assert(result.existingParts.isEmpty)
      }
    }
  }
}
