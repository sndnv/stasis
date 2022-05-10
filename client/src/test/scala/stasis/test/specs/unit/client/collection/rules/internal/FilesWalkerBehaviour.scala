package stasis.test.specs.unit.client.collection.rules.internal

import stasis.client.collection.rules.internal.FilesWalker
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.client.ResourceHelpers

trait FilesWalkerBehaviour { _: UnitSpec with ResourceHelpers =>
  import ResourceHelpers._

  def walker(setup: FileSystemSetup): Unit =
    it should "support filtering files and directories based on a provided matcher" in {
      val (filesystem, _) = createMockFileSystem(setup)

      val matcher = filesystem.getPathMatcher("glob:/work/root/parent-*/*-{a,b,c}")

      val successfulResult = FilesWalker.filter(start = filesystem.getPath("/work/root/parent-1"), matcher = matcher)

      val failedResult = FilesWalker.filter(start = filesystem.getPath("/work/root/other"), matcher = matcher)

      successfulResult.isEmpty should be(false)

      successfulResult.matches.map(_.toString) should be(
        Seq(
          "/work/root/parent-1/child-dir-a",
          "/work/root/parent-1/child-dir-b",
          "/work/root/parent-1/child-dir-c"
        )
      )

      successfulResult.failures should be(empty)

      failedResult.isEmpty should be(false)
      failedResult.matches should be(empty)

      failedResult.failures.map { case (k, v) => k.toString -> v.toString } should be(
        Map("/work/root/other" -> "java.nio.file.NoSuchFileException: /work/root/other")
      )
    }
}
