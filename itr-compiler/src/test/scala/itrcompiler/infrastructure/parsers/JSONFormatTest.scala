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

  it should "parse equal CUBatch across key orders (key-order)" in {
    val orderA = """[{"cu-id":"cu-1","cu-type":"regular","dtr-coordinates":["TYPE.A"],"content":"hello"}]"""
    val orderB = """[{"content":"hello","dtr-coordinates":["TYPE.A"],"cu-type":"regular","cu-id":"cu-1"}]"""
    val orderC = """[{"dtr-coordinates":["TYPE.A"],"content":"hello","cu-id":"cu-1","cu-type":"regular"}]"""
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

  it should "split all four inline sections when all present" in {
    val content = "REQUIREMENTS: req body\nCOORDINATES: coord body\nIMPLEMENTATION_STEPS: steps body\nACCEPTANCE: acc body"
    val json = """[{"cu-id":"cu-split","dtr-coordinates":[],"content":"""" + content.replace("\n", "\\n") + """"}]"""
    val batch = parser.parse(json)
    batch.cus.size shouldBe 1
    val comps = batch.cus.head.components
    comps.size shouldBe 4
    comps("requirements") shouldBe "req body"
    comps("coordinates") shouldBe "coord body"
    comps("implementation-steps") shouldBe "steps body"
    comps("acceptance") shouldBe "acc body"
  }

  it should "split inline sections when one section is missing" in {
    val content = "REQUIREMENTS: req body\nCOORDINATES: coord body\nIMPLEMENTATION_STEPS: steps body"
    val json = """[{"cu-id":"cu-partial","dtr-coordinates":[],"content":"""" + content.replace("\n", "\\n") + """"}]"""
    val batch = parser.parse(json)
    batch.cus.size shouldBe 1
    val comps = batch.cus.head.components
    comps.size shouldBe 3
    comps("requirements") shouldBe "req body"
    comps("coordinates") shouldBe "coord body"
    comps("implementation-steps") shouldBe "steps body"
    comps.contains("acceptance") shouldBe false
  }

  it should "leave components empty when no inline sections are present" in {
    val json = """[{"cu-id":"cu-plain","dtr-coordinates":[],"content":"just plain prose with no sections"}]"""
    val batch = parser.parse(json)
    batch.cus.size shouldBe 1
    batch.cus.head.components shouldBe Map.empty
  }
}
