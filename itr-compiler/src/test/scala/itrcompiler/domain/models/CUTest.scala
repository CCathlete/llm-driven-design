package itrcompiler.domain.models

import org.scalatest.funspec.AnyFunSpec
import java.time.Instant

class CUTest extends AnyFunSpec {
  describe("CU") {
    it("should create a CU with required fields") {
      val cu = CU(id = "cu-001", dtrCoordinates = Seq("TYPE.Foo"), content = "some content")
      assert(cu.id == "cu-001")
      assert(cu.dtrCoordinates == Seq("TYPE.Foo"))
      assert(cu.content == "some content")
    }

    it("should auto-generate a timestamp") {
      val cu = CU(id = "cu-001", dtrCoordinates = Seq.empty, content = "")
      assert(cu.timestamp.isBefore(Instant.now().plusSeconds(1)))
    }

    it("should allow empty coordinates") {
      val cu = CU(id = "cu-002", dtrCoordinates = Seq.empty, content = "content")
      assert(cu.dtrCoordinates.isEmpty)
    }

    it("should be a Model") {
      val cu = CU(id = "cu-003", dtrCoordinates = Seq.empty, content = "")
      assert(cu.isInstanceOf[Model])
    }

    it("should report isRequiredPart for special CU types") {
      val archCu = CU(id = "arch", dtrCoordinates = Seq.empty, content = "", cuType = ArchCU)
      val legendCu = CU(id = "legend", dtrCoordinates = Seq.empty, content = "", cuType = LegendCU)
      val verifCu = CU(id = "verification", dtrCoordinates = Seq.empty, content = "", cuType = VerificationCU)
      val e2eCu = CU(id = "e2everification", dtrCoordinates = Seq.empty, content = "", cuType = E2eVerificationCU)
      val regularCu = CU(id = "cu-001", dtrCoordinates = Seq.empty, content = "")

      assert(archCu.isRequiredPart)
      assert(legendCu.isRequiredPart)
      assert(verifCu.isRequiredPart)
      assert(e2eCu.isRequiredPart)
      assert(!regularCu.isRequiredPart)
    }

    it("should return correct fileName per CU type") {
      assert(CU(id = "arch", dtrCoordinates = Seq.empty, content = "", cuType = ArchCU).fileName == "ARCH.itr")
      assert(CU(id = "legend", dtrCoordinates = Seq.empty, content = "", cuType = LegendCU).fileName == "LEGEND.itr")
      assert(CU(id = "my-verification", dtrCoordinates = Seq.empty, content = "", cuType = VerificationCU).fileName == "my-verification.verification.itr")
      assert(CU(id = "e2everification", dtrCoordinates = Seq.empty, content = "", cuType = E2eVerificationCU).fileName == "E2EVERIFICATION.itr")
      assert(CU(id = "cu-001", dtrCoordinates = Seq.empty, content = "").fileName == "cu-001.itr")
    }

    it("should contain all four required parts") {
      assert(CU.requiredParts == Set(ArchCU, LegendCU, VerificationCU, E2eVerificationCU))
    }
  }

  describe("CUType") {
    it("should parse standard names") {
      assert(CUType.fromString("arch") == ArchCU)
      assert(CUType.fromString("legend") == LegendCU)
      assert(CUType.fromString("verification") == VerificationCU)
      assert(CUType.fromString("e2everification") == E2eVerificationCU)
    }

    it("should parse CU-suffixed names") {
      assert(CUType.fromString("archcu") == ArchCU)
      assert(CUType.fromString("legendcu") == LegendCU)
      assert(CUType.fromString("verificationcu") == VerificationCU)
      assert(CUType.fromString("e2everificationcu") == E2eVerificationCU)
    }

    it("should parse hyphenated e2e-verification") {
      assert(CUType.fromString("e2e-verification") == E2eVerificationCU)
      assert(CUType.fromString("e2e-verificationcu") == E2eVerificationCU)
    }

    it("should be case-insensitive") {
      assert(CUType.fromString("ARCH") == ArchCU)
      assert(CUType.fromString("E2E-VERIFICATION") == E2eVerificationCU)
      assert(CUType.fromString("Legend") == LegendCU)
    }

    it("should default to RegularCU for unknown types") {
      assert(CUType.fromString("custom") == RegularCU)
      assert(CUType.fromString("") == RegularCU)
      assert(CUType.fromString("foo") == RegularCU)
    }
  }
}
