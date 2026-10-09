package itrcompiler.domain.models

import org.scalatest.funspec.AnyFunSpec

class CUSynopsisTest extends AnyFunSpec {
  describe("CUSynopsis") {
    it("should default maxAttempts to 3") {
      val synopsis = CUSynopsis(features = Seq("feat-1"), summary = "done")
      assert(synopsis.maxAttempts == 3)
    }

    it("should report isComplete false on empty summary") {
      val emptySummary = CUSynopsis(features = Seq("feat-1"), summary = "")
      assert(!emptySummary.isComplete)
      val blankSummary = CUSynopsis(features = Seq("feat-1"), summary = "   ")
      assert(!blankSummary.isComplete)
      val noFeatures = CUSynopsis(features = Seq.empty, summary = "done")
      assert(!noFeatures.isComplete)
    }

    it("should report isComplete true when features and summary present") {
      val synopsis = CUSynopsis(features = Seq("feat-1"), summary = "done")
      assert(synopsis.isComplete)
    }

    it("should be a Model") {
      val synopsis = CUSynopsis()
      assert(synopsis.isInstanceOf[Model])
    }
  }

  describe("CUBatch synopsis") {
    it("should carry synopsis data") {
      val synopsis = CUSynopsis(
        features = Seq("feat-1"),
        maxAttempts = 3,
        summary = "Per-CU ITR directories"
      )
      val batch = CUBatch(cus = Seq.empty, synopsis = Some(synopsis))
      assert(batch.synopsis.isDefined)
      assert(batch.synopsis.get.isComplete)
      assert(batch.synopsis.get.maxAttempts == 3)
    }

    it("should default to no synopsis for backward compatibility") {
      val batch = CUBatch(cus = Seq.empty)
      assert(batch.synopsis.isEmpty)
    }
  }

  describe("CU components") {
    it("should have empty missingComponents when all four component keys present") {
      val cu = CU(
        id = "cu-001",
        dtrCoordinates = Seq.empty,
        content = "",
        components = Map(
          "coordinates" -> "c",
          "requirements" -> "r",
          "implementation-steps" -> "i",
          "acceptance" -> "a"
        )
      )
      assert(cu.missingComponents.isEmpty)
    }

    it("should report exactly the absent component keys") {
      val cu = CU(
        id = "cu-00x",
        dtrCoordinates = Seq.empty,
        content = "",
        components = Map("coordinates" -> "c")
      )
      assert(cu.missingComponents == Set("requirements", "implementation-steps", "acceptance"))
    }
  }
}
