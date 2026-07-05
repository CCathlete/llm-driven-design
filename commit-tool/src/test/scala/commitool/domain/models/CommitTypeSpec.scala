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
      CommitType.fromString(typeString) should matchPattern {
        case Right(commitType) if commitType.label == typeString =>
      }
    }
  }

  it should "return UnknownType for invalid type" in {
    val invalidType = "invalid"
    val validTypesString = CommitType.allTypes.map(_.label).mkString("|")

    CommitType.fromString(invalidType) should matchPattern {
      case Left(UnknownType(got, valid)) if got == invalidType && valid == validTypesString =>
    }
  }
}
