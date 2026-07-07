package commitool.domain.models

import commitool.domain.models.{CommitType, UnknownType}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CommitTypeSpec extends AnyFlatSpec with Matchers {

  "CommitType.fromString" should "parse all valid types correctly" in {
    val validTypes = List(
      "feat", "fix", "docs", "style", "refactor",
      "perf", "test", "build", "ci", "chore", "revert"
    )

    validTypes.foreach { typeString =>
      val result = CommitType.fromString(typeString)
      result.isRight shouldBe true
      result.map(_.label) shouldBe Right(typeString)
    }
  }

  it should "return UnknownType for invalid type" in {
    val invalidType = "invalid"
    val validTypesString = CommitType.allTypes.map(_.label).mkString("|")

    val result = CommitType.fromString(invalidType)
    result match {
      case Left(UnknownType(got, valid)) =>
        got shouldBe invalidType
        valid shouldBe validTypesString
      case other => fail(s"Expected Left(UnknownType(...)) but got $other")
    }
  }
}
