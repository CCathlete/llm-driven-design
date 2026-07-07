package itrcompiler.infrastructure.parsers

import org.scalatest.funspec.AnyFunSpec

class YAMLFormatTest extends AnyFunSpec {
  val format = new YAMLFormat

  describe("YAMLFormat") {
    describe("parse") {
      it("should parse a single entry") {
        val yaml = """cu-001:
                     |  dtr-coordinates: [TYPE.Foo]
                     |  content: "hello"
                     |""".stripMargin
        val batch = format.parse(yaml)
        assert(batch.cus.size == 1)
        assert(batch.cus.head.id == "cu-001")
        assert(batch.cus.head.dtrCoordinates == Seq("TYPE.Foo"))
        assert(batch.cus.head.content == "hello")
      }

      it("should parse multiple entries") {
        val yaml = """cu-a:
                     |  dtr-coordinates: []
                     |  content: "x"
                     |
                     |cu-b:
                     |  dtr-coordinates: [TYPE.B]
                     |  content: "y"
                     |""".stripMargin
        val batch = format.parse(yaml)
        assert(batch.cus.size == 2)
      }

      it("should handle empty coordinates") {
        val yaml = """cu-001:
                     |  dtr-coordinates: []
                     |  content: "test"
                     |""".stripMargin
        val batch = format.parse(yaml)
        assert(batch.cus.head.dtrCoordinates.isEmpty)
      }

      it("should handle empty input") {
        val batch = format.parse("")
        assert(batch.cus.isEmpty)
      }
    }

    describe("serialize") {
      it("should produce valid YAML") {
        import itrcompiler.domain.models.{CU, CUBatch}
        val batch = CUBatch(Seq(CU(id = "cu-001", dtrCoordinates = Seq("TYPE.Foo"), content = "hello")))
        val yaml = format.serialize(batch)
        assert(yaml.contains("cu-001"))
        assert(yaml.contains("TYPE.Foo"))
      }

      it("should round-trip") {
        import itrcompiler.domain.models.{CU, CUBatch}
        val original = CUBatch(Seq(
          CU(id = "a", dtrCoordinates = Seq("X", "Y"), content = "hello world")
        ))
        val yaml = format.serialize(original)
        val parsed = format.parse(yaml)
        assert(parsed.cus.size == 1)
        assert(parsed.cus.head.id == "a")
      }
    }
  }
}
