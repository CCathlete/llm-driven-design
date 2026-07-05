package itrcompiler.domain.models

import org.scalatest.funspec.AnyFunSpec

class CUBatchTest extends AnyFunSpec {
  describe("CUBatch") {
    it("should create a batch with CUs") {
      val cus = Seq(
        CU(id = "cu-001", dtrCoordinates = Seq.empty, content = "a"),
        CU(id = "cu-002", dtrCoordinates = Seq.empty, content = "b")
      )
      val batch = CUBatch(cus)
      assert(batch.cus.size == 2)
    }

    it("should allow empty batch") {
      val batch = CUBatch(Seq.empty)
      assert(batch.cus.isEmpty)
    }

    it("should be a Model") {
      val batch = CUBatch(Seq.empty)
      assert(batch.isInstanceOf[Model])
    }
  }
}
