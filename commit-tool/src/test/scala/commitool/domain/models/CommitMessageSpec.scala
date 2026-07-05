package commitool.domain.models

import commitool.domain.models.{CommitMessage, CommitType, InvalidFormat, MissingScope, UnknownType, EmptyBody}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CommitMessageSpec extends AnyFlatSpec with Matchers {

  "CommitMessage.fromRaw" should "parse valid message correctly" in {
    val rawMessage = "feat(scope): description\n\nbody content\nmore body content"

    CommitMessage.fromRaw(rawMessage) should matchPattern {
      case Right(CommitMessage(CommitType.Feat, "scope", "description", body)) if body == "body content\nmore body content" =>
    }
  }

  it should "return InvalidFormat for no parentheses" in {
    val rawMessage = "feat: description"
    CommitMessage.fromRaw(rawMessage) should matchPattern {
      case Left(InvalidFormat(line)) if line == "feat: description" =>
    }
  }

  it should "return MissingScope for empty scope" in {
    val rawMessage = "feat(): description"
    CommitMessage.fromRaw(rawMessage) should matchPattern {
      case Left(MissingScope(line)) if line == "feat(): description" =>
    }
  }

  it should "return UnknownType for invalid type" in {
    val rawMessage = "invalid(scope): description"
    CommitMessage.fromRaw(rawMessage) should matchPattern {
      case Left(UnknownType(got, _)) if got == "invalid" =>
    }
  }

  it should "return EmptyBody for missing body" in {
    val rawMessage = "feat(scope): description"
    CommitMessage.fromRaw(rawMessage) should matchPattern {
      case Left(EmptyBody) =>
    }
  }

  it should "return InvalidFormat for empty input" in {
    CommitMessage.fromRaw("") should matchPattern {
      case Left(InvalidFormat("")) =>
    }
  }

  it should "handle edge cases correctly" in {
    // Test with special characters in description
    val specialCharsMessage = "feat(scope): description with !@#$%^&*()"
    CommitMessage.fromRaw(specialCharsMessage + "\n\nbody") should matchPattern {
      case Right(CommitMessage(_, _, description, _)) if description == "description with !@#$%^&*()" =>
    }
  }

  it should "preserve multi-paragraph body with blank lines" in {
    val raw = "feat(scope): description\n\nParagraph one.\n\nParagraph two.\n\nParagraph three."
    CommitMessage.fromRaw(raw) should matchPattern {
      case Right(CommitMessage(_, _, _, body)) if body == "Paragraph one.\n\nParagraph two.\n\nParagraph three." =>
    }
  }
}
