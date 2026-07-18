package itrcompiler.infrastructure.parsers

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import itrcompiler.domain.models._

class JSONFormatTest extends AnyFlatSpec with Matchers {

  val parser = new JSONFormat

  "JSONFormat" should "parse large content strings correctly" in {
    val largeContent = """[{"cu-id":"cu-large","dtr-coordinates":[],"content":""" + "\"" + "a" * 10000 + "\"}]"
    val batch = parser.parse(largeContent)
    batch.cus should not be empty
    batch.cus.head.content shouldBe "a" * 10000
  }

  it should "handle cu-type field correctly" in {
    val jsonWithCuType = """[{"cu-id":"cu-test","cu-type":"arch","dtr-coordinates":[],"content":"test content"}]"""
    val batch = parser.parse(jsonWithCuType)
    batch.cus should not be empty
    batch.cus.head.cuType shouldBe ArchCU
  }

  it should "parse CUs without cu-type field (backward compatible)" in {
    val jsonWithoutType = """[{"cu-id":"cu-old","dtr-coordinates":["TYPE.X"],"content":"old format"}]"""
    val batch = parser.parse(jsonWithoutType)
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
