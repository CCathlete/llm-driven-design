package itrcompiler.application.services

import itrcompiler.domain.models.{CU, CUBatch}
import itrcompiler.application.ports.ContentRead
import org.scalatest.funspec.AnyFunSpec
import java.nio.file.Paths

class ContentDeserializeTest extends AnyFunSpec {
  describe("ContentDeserialize") {
    it("should delegate JSON deserialization to ContentRead port") {
      val expected = CUBatch(Seq(CU(id = "test", dtrCoordinates = Seq.empty, content = "hello")))
      val port = new ContentRead {
        def readJson(path: Path) = expected
        def readYaml(path: Path) = CUBatch(Seq.empty)
      }
      val deser = new ContentDeserialize(port)
      val result = deser.fromJson(Paths.get("test.json"))
      assert(result == expected)
    }

    it("should delegate YAML deserialization to ContentRead port") {
      val expected = CUBatch(Seq(CU(id = "test", dtrCoordinates = Seq.empty, content = "world")))
      val port = new ContentRead {
        def readJson(path: Path) = CUBatch(Seq.empty)
        def readYaml(path: Path) = expected
      }
      val deser = new ContentDeserialize(port)
      val result = deser.fromYaml(Paths.get("test.yaml"))
      assert(result == expected)
    }
  }
}
