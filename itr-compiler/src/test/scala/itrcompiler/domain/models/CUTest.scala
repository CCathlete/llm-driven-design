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
  }
}
