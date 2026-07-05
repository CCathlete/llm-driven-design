package itrcompiler.application.services

import org.scalatest.funspec.AnyFunSpec

class CoordinateRulesTest extends AnyFunSpec {
  val rules = new CoordinateRules

  describe("CoordinateRules") {
    it("should pass through valid coordinates unchanged") {
      val coords = Seq("TYPE.Foo", "FILE.src/Foo.scala")
      assert(rules.validate(coords) == coords)
    }

    it("should trim whitespace from coordinates") {
      val result = rules.validate(Seq("  TYPE.Foo  ", "\tFILE.src/Bar.scala"))
      assert(result == Seq("TYPE.Foo", "FILE.src/Bar.scala"))
    }

    it("should filter out blank coordinates") {
      val result = rules.validate(Seq("TYPE.Foo", "", "  ", "TYPE.Bar"))
      assert(result == Seq("TYPE.Foo", "TYPE.Bar"))
    }

    it("should return empty for all-blank input") {
      assert(rules.validate(Seq("", "  ")).isEmpty)
    }

    it("should return empty for empty input") {
      assert(rules.validate(Seq.empty).isEmpty)
    }
  }
}
