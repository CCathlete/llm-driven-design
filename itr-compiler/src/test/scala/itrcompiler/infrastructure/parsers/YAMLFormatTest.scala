package itrcompiler.infrastructure.parsers

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import itrcompiler.domain.models._

class YAMLFormatTest extends AnyFlatSpec with Matchers {

  val parser = new YAMLFormat

  "YAMLFormat" should "handle cu-type field correctly" in {
    val yamlWithCuType = """cu-test:
  cu-type: arch
  dtr-coordinates: []
  content: "test content"
"""
    val batch = parser.parse(yamlWithCuType)
    batch.cus should not be empty
    batch.cus.head.cuType shouldBe ArchCU
  }

  it should "parse CUs without cu-type field (backward compatible)" in {
    val yamlWithoutType = """cu-old:
  dtr-coordinates: [TYPE.X]
  content: "old format"
"""
    val batch = parser.parse(yamlWithoutType)
    batch.cus should not be empty
    batch.cus.head.cuType shouldBe RegularCU
  }

  it should "serialize and parse back correctly" in {
    val batch = CUBatch(Seq(
      CU(id = "cu-1", dtrCoordinates = Seq("TYPE.A"), content = "hello", cuType = ArchCU),
      CU(id = "cu-2", dtrCoordinates = Seq("TYPE.B"), content = "world", cuType = RegularCU)
    ))
    val serialized = parser.serialize(batch)
    val parsed = parser.parse(serialized)
    parsed.cus.size shouldBe 2
    parsed.cus.head.id shouldBe "cu-1"
    parsed.cus.head.cuType shouldBe ArchCU
    parsed.cus(1).id shouldBe "cu-2"
    parsed.cus(1).cuType shouldBe RegularCU
  }

  it should "parse equal CUBatch across key orders (key-order proves order-independence)" in {
    val orderA = """cu-1:
  cu-type: regular
  dtr-coordinates: ["TYPE.A"]
  content: "hello"
"""
    val orderB = """cu-1:
  content: "hello"
  dtr-coordinates: ["TYPE.A"]
  cu-type: regular
"""
    val orderC = """cu-1:
  dtr-coordinates: ["TYPE.A"]
  content: "hello"
  cu-type: regular
"""
    val a = parser.parse(orderA)
    val b = parser.parse(orderB)
    val c = parser.parse(orderC)
    a.cus.size shouldBe 1
    b.cus.size shouldBe 1
    c.cus.size shouldBe 1
    b.cus.head.id shouldBe a.cus.head.id
    b.cus.head.content shouldBe a.cus.head.content
    b.cus.head.dtrCoordinates shouldBe a.cus.head.dtrCoordinates
    b.cus.head.cuType shouldBe a.cus.head.cuType
    c.cus.head.id shouldBe a.cus.head.id
    c.cus.head.content shouldBe a.cus.head.content
    c.cus.head.dtrCoordinates shouldBe a.cus.head.dtrCoordinates
    c.cus.head.cuType shouldBe a.cus.head.cuType
  }
}
