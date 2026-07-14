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
}
