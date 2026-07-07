package commitool.domain.models

import commitool.domain.models.{Changelist, ChangeStatus, StagedChange}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ChangelistSpec extends AnyFlatSpec with Matchers {

  "Changelist.formatAsString" should "format mixed changes correctly" in {
    val changes = List(
      StagedChange(ChangeStatus.Added, "file1.txt"),
      StagedChange(ChangeStatus.Modified, "file2.txt"),
      StagedChange(ChangeStatus.Deleted, "file3.txt")
    )
    val changelist = Changelist(changes)

    val expected = "Changes to be committed:\n  new file:\tfile1.txt\n  modified:\tfile2.txt\n  deleted:\tfile3.txt"

    changelist.formatAsString should equal (expected)
  }

  it should "return empty string for empty changelist" in {
    val changelist = Changelist(Nil)
    changelist.formatAsString shouldBe ""
  }

  it should "format single change correctly" in {
    val changes = List(
      StagedChange(ChangeStatus.Modified, "single.txt")
    )
    val changelist = Changelist(changes)

    val expected = "Changes to be committed:\n  modified:\tsingle.txt"

    changelist.formatAsString should equal (expected)
  }

  it should "handle special characters in file paths" in {
    val changes = List(
      StagedChange(ChangeStatus.Added, "path with spaces/file.txt"),
      StagedChange(ChangeStatus.Modified, "path/with/special!@#$%^&*().txt")
    )
    val changelist = Changelist(changes)

    val expected = "Changes to be committed:\n  new file:\tpath with spaces/file.txt\n  modified:\tpath/with/special!@#$%^&*().txt"

    changelist.formatAsString should equal (expected)
  }
}
