package itrcompiler.application.services

import itrcompiler.domain.models.CU
import itrcompiler.application.ports.CUWrite
import org.scalatest.funspec.AnyFunSpec
import java.nio.file.Paths

class CUStoreTest extends AnyFunSpec {
  describe("CUStore") {
    it("should delegate write to CUWrite port") {
      var written: Option[CU] = None
      val port = new CUWrite {
        def write(cu: CU, outFolder: java.nio.file.Path, force: Boolean): Unit =
          written = Some(cu)
      }
      val store = new CUStore(port)
      val cu = CU(id = "test", dtrCoordinates = Seq.empty, content = "data")
      store.store(cu, Paths.get("out"), force = false)
      assert(written.contains(cu))
    }
  }
}
