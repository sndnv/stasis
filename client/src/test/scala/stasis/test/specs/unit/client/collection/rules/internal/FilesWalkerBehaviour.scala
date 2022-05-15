package stasis.test.specs.unit.client.collection.rules.internal

import stasis.client.collection.rules.Rule
import stasis.client.collection.rules.internal.{FilesWalker, IndexedRule}
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.client.ResourceHelpers

import java.nio.file.Path
import scala.collection.mutable

trait FilesWalkerBehaviour { _: UnitSpec with ResourceHelpers =>
  import ResourceHelpers._

  def walker(setup: FileSystemSetup): Unit = {
    it should "support filtering files and directories based on provided matchers" in {
      val (filesystem, _) = createMockFileSystem(setup)

      val original = Rule.Original(line = "", lineNumber = 0)

      val rule1 = Rule(operation = Rule.Operation.Include, directory = "/", pattern = "*", comment = None, original = original)
      val matcher1 = filesystem.getPathMatcher("glob:/work/root/parent-*/*-{a,b,c}")

      val rule2 = Rule(operation = Rule.Operation.Exclude, directory = "/", pattern = "*", comment = None, original = original)
      val matcher2 = filesystem.getPathMatcher("glob:/work/root/parent-*/*-{d,e}")

      val matchers = Seq(
        IndexedRule(index = 0, underlying = rule1) -> matcher1,
        IndexedRule(index = 1, underlying = rule2) -> matcher2
      )

      val matchedSuccessful = mutable.ListBuffer[Path]()
      val successfulResult = FilesWalker.filter(
        start = filesystem.getPath("/work/root/parent-1"),
        onMatchIncluded = matchedSuccessful.addOne,
        matchers = matchers
      )

      matchedSuccessful.map(_.toString).toList should be(
        List(
          "/work/root/parent-1/child-dir-a",
          "/work/root/parent-1/child-dir-b",
          "/work/root/parent-1/child-dir-c"
        )
      )

      successfulResult.isEmpty should be(false)

      successfulResult.matches.map { case (k, v) =>
        k.underlying.asString -> v.map(_.toString)
      } should be(
        Map(
          "+ / *" -> Seq(
            "/work/root/parent-1/child-dir-a",
            "/work/root/parent-1/child-dir-b",
            "/work/root/parent-1/child-dir-c"
          ),
          "- / *" -> Seq(
            "/work/root/parent-1/child-dir-d",
            "/work/root/parent-1/child-dir-e"
          )
        )
      )

      successfulResult.failures should be(empty)

      val matchedFailed = mutable.ListBuffer[Path]()
      val failedResult = FilesWalker.filter(
        start = filesystem.getPath("/work/root/other"),
        onMatchIncluded = matchedFailed.addOne,
        matchers = matchers
      )

      matchedFailed.map(_.toString).toList should be(List.empty)
      failedResult.isEmpty should be(false)
      failedResult.matches should be(empty)

      failedResult.failures.map { case (k, v) => k.toString -> v.toString } should be(
        Map("/work/root/other" -> "java.nio.file.NoSuchFileException: /work/root/other")
      )
    }

    it should "skipping excluded subtrees" in {
      val (filesystem, _) = createMockFileSystem(setup)

      val original = Rule.Original(line = "", lineNumber = 0)

      val rule1 = Rule(operation = Rule.Operation.Include, directory = "/", pattern = "*", comment = None, original = original)
      val matcher1 = filesystem.getPathMatcher("glob:/work/root/parent-{0,1}/*-{a,b,c}/*")

      val rule2 = Rule(operation = Rule.Operation.Exclude, directory = "/", pattern = "*", comment = None, original = original)
      val matcher2 = filesystem.getPathMatcher("glob:/work/root/parent-{0,1}/*-{c,d,e}")

      val matchers = Seq(
        IndexedRule(index = 0, underlying = rule1) -> matcher1,
        IndexedRule(index = 1, underlying = rule2) -> matcher2
      )

      val result = FilesWalker.filter(start = filesystem.getPath("/work/root"), onMatchIncluded = _ => (), matchers = matchers)

      result.isEmpty should be(false)

      result.failures should be(empty)

      val (included, excluded) = result.matches.toSeq
        .partition(_._1.underlying.operation == Rule.Operation.Include)

      included.flatMap(_._2).map(_.toString).foreach { path =>
        path should not include "child-dir-c"
        path should not include "child-dir-d"
        path should not include "child-dir-e"
      }

      excluded.flatMap(_._2).map(_.toString) should be(
        Seq(
          "/work/root/parent-0/child-dir-c",
          "/work/root/parent-0/child-dir-d",
          "/work/root/parent-0/child-dir-e",
          "/work/root/parent-1/child-dir-c",
          "/work/root/parent-1/child-dir-d",
          "/work/root/parent-1/child-dir-e"
        )
      )
    }
  }
}
