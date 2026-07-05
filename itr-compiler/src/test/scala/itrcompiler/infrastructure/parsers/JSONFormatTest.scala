package itrcompiler.infrastructure.parsers

import org.scalatest.funspec.AnyFunSpec

class JSONFormatTest extends AnyFunSpec {
  val format = new JSONFormat

  describe("JSONFormat") {
    describe("parse") {
      it("should parse a JSON array with one entry") {
        val json = """[{"cu-id":"cu-001","dtr-coordinates":["TYPE.Foo"],"content":"hello"}]"""
        val batch = format.parse(json)
        assert(batch.cus.size == 1)
        assert(batch.cus.head.id == "cu-001")
        assert(batch.cus.head.dtrCoordinates == Seq("TYPE.Foo"))
        assert(batch.cus.head.content == "hello")
      }

      it("should parse multiple entries") {
        val json = """[{"cu-id":"a","dtr-coordinates":[],"content":"x"},{"cu-id":"b","dtr-coordinates":[],"content":"y"}]"""
        val batch = format.parse(json)
        assert(batch.cus.size == 2)
      }

      it("should handle empty coordinates") {
        val json = """[{"cu-id":"cu-001","dtr-coordinates":[],"content":"test"}]"""
        val batch = format.parse(json)
        assert(batch.cus.head.dtrCoordinates.isEmpty)
      }

      it("should handle empty array") {
        val batch = format.parse("[]")
        assert(batch.cus.isEmpty)
      }

      it("should handle empty string") {
        val batch = format.parse("")
        assert(batch.cus.isEmpty)
      }

      it("should unescape newlines in content") {
        val json = """[{"cu-id":"cu-001","dtr-coordinates":[],"content":"line1\\nline2"}]"""
        val batch = format.parse(json)
        assert(batch.cus.head.content == "line1\nline2")
      }
    }

    describe("serialize") {
      it("should produce valid JSON") {
        import itrcompiler.domain.models.{CU, CUBatch}
        val batch = CUBatch(Seq(CU(id = "cu-001", dtrCoordinates = Seq("TYPE.Foo"), content = "hello")))
        val json = format.serialize(batch)
        assert(json.contains("cu-001"))
        assert(json.contains("TYPE.Foo"))
        assert(json.contains("hello"))
      }

      it("should round-trip") {
        import itrcompiler.domain.models.{CU, CUBatch}
        val original = CUBatch(Seq(
          CU(id = "a", dtrCoordinates = Seq("X", "Y"), content = "hello\nworld")
        ))
        val json = format.serialize(original)
        val parsed = format.parse(json)
        assert(parsed.cus.size == 1)
        assert(parsed.cus.head.id == "a")
        assert(parsed.cus.head.content == "hello\nworld")
      }
    }
  }
}
