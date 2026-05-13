package stasis.test.specs.unit.client.collection.rules.internal

import java.nio.file.Path

import scala.collection.mutable

import stasis.client.collection.rules.Rule
import stasis.client.collection.rules.internal.FilesWalker
import stasis.client.collection.rules.internal.IndexedRule
import io.github.sndnv.layers.testing.FileSystemHelpers.FileSystemSetup
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.client.ResourceHelpers

trait FilesWalkerBehaviour { _: UnitSpec with ResourceHelpers =>
  def walker(setup: FileSystemSetup): Unit = {
    it should "support filtering files and directories based on provided matchers" in {
      val (filesystem, _) = createMockFileSystem(setup)

      val original = Rule.Original(line = "", lineNumber = 0)

      val rule1 = Rule(
        operation = Rule.Operation.Include,
        directory = "/".normalized(setup),
        pattern = "*",
        comment = None,
        original = original
      )
      val matcher1 = filesystem.getPathMatcher("glob:/work/root/parent-*/*-{a,b,c}".normalizedGlob(setup))

      val rule2 = Rule(
        operation = Rule.Operation.Exclude,
        directory = "/".normalized(setup),
        pattern = "*",
        comment = None,
        original = original
      )
      val matcher2 = filesystem.getPathMatcher("glob:/work/root/parent-*/*-{d,e}".normalizedGlob(setup))

      val matchers = Seq(
        IndexedRule(index = 0, underlying = rule1) -> matcher1,
        IndexedRule(index = 1, underlying = rule2) -> matcher2
      )

      val matchedSuccessful = mutable.ListBuffer[Path]()
      val successfulResult = FilesWalker.filter(
        start = filesystem.getPath("/work/root/parent-1".normalized(setup)),
        onMatchIncluded = matchedSuccessful.addOne,
        matchers = matchers
      )

      matchedSuccessful.map(_.toString).toList should be(
        List(
          "/work/root/parent-1/child-dir-a".normalized(setup),
          "/work/root/parent-1/child-dir-b".normalized(setup),
          "/work/root/parent-1/child-dir-c".normalized(setup)
        )
      )

      successfulResult.isEmpty should be(false)

      successfulResult.matches.map { case (k, v) =>
        k.underlying.asString -> v.map(_.toString)
      } should be(
        Map(
          "+ / *".normalized(setup) -> Seq(
            "/work/root/parent-1/child-dir-a".normalized(setup),
            "/work/root/parent-1/child-dir-b".normalized(setup),
            "/work/root/parent-1/child-dir-c".normalized(setup)
          ),
          "- / *".normalized(setup) -> Seq(
            "/work/root/parent-1/child-dir-d".normalized(setup),
            "/work/root/parent-1/child-dir-e".normalized(setup)
          )
        )
      )

      successfulResult.failures should be(empty)

      val matchedFailed = mutable.ListBuffer[Path]()
      val failedResult = FilesWalker.filter(
        start = filesystem.getPath("/work/root/other".normalized(setup)),
        onMatchIncluded = matchedFailed.addOne,
        matchers = matchers
      )

      matchedFailed.map(_.toString).toList should be(List.empty)
      failedResult.isEmpty should be(false)
      failedResult.matches should be(empty)

      failedResult.failures.map { case (k, v) => k.toString -> v.toString } should be(
        Map("/work/root/other".normalized(setup) -> s"java.nio.file.NoSuchFileException: ${"/work/root/other".normalized(setup)}")
      )
    }

    it should "support skipping excluded subtrees" in {
      val (filesystem, _) = createMockFileSystem(setup)

      val original = Rule.Original(line = "", lineNumber = 0)

      val rule1 = Rule(
        operation = Rule.Operation.Include,
        directory = "/".normalized(setup),
        pattern = "*",
        comment = None,
        original = original
      )
      val matcher1 = filesystem.getPathMatcher("glob:/work/root/parent-{0,1}/*-{a,b,c}/*".normalizedGlob(setup))

      val rule2 = Rule(
        operation = Rule.Operation.Exclude,
        directory = "/".normalized(setup),
        pattern = "*",
        comment = None,
        original = original
      )
      val matcher2 = filesystem.getPathMatcher("glob:/work/root/parent-{0,1}/*-{c,d,e}".normalizedGlob(setup))

      val matchers = Seq(
        IndexedRule(index = 0, underlying = rule1) -> matcher1,
        IndexedRule(index = 1, underlying = rule2) -> matcher2
      )

      val result = FilesWalker.filter(
        start = filesystem.getPath("/work/root".normalized(setup)),
        onMatchIncluded = _ => (),
        matchers = matchers
      )

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
          "/work/root/parent-0/child-dir-c".normalized(setup),
          "/work/root/parent-0/child-dir-d".normalized(setup),
          "/work/root/parent-0/child-dir-e".normalized(setup),
          "/work/root/parent-1/child-dir-c".normalized(setup),
          "/work/root/parent-1/child-dir-d".normalized(setup),
          "/work/root/parent-1/child-dir-e".normalized(setup)
        )
      )
    }
  }

  private implicit class ExtendedString(string: String) {
    def normalized(setup: FileSystemSetup): String =
      if (setup.name == "Windows") string.replaceFirst("/", "C:/").replaceAll("/", "\\\\")
      else string

    def normalizedGlob(setup: FileSystemSetup): String =
      if (setup.name == "Windows") string.replaceFirst("/", "C:/")
      else string
  }
}
