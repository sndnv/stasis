package stasis.test.specs.unit.client.collection.rules

import java.nio.file.NoSuchFileException
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.ExecutionContext

import stasis.client.collection.rules.Rule
import stasis.client.collection.rules.Specification
import stasis.client.collection.rules.exceptions.RuleMatchingFailure
import stasis.client.collection.rules.internal.IndexedRule
import stasis.layers.FileSystemHelpers.FileSystemSetup
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers

trait SpecificationBehaviour { _: AsyncUnitSpec with ResourceHelpers =>
  import ResourceHelpers._

  private val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  def specification(setup: FileSystemSetup): Unit = {
    it should "require at least one rule to be present" in {
      import Specification.ExtendedRules

      val rules = Seq(IndexedRule(index = 0, underlying = Rule("+ /work ?", 0).get))

      noException should be thrownBy rules.first
      an[IllegalStateException] should be thrownBy Seq.empty[IndexedRule].first
    }

    it should "support creation based on rules" in {
      val (filesystem, objects) = createMockFileSystem(setup)

      objects.filesPerDir should be > 0
      objects.rootDirs should be > 0
      objects.nestedDirs should be > 0

      val rule1 = "+ /work      ?                  # incl all files in the root work directory"
      val rule2 = "- /work      [a-z]              # excl all files in the root work directory in the range"
      val rule3 = "- /work      {0|1}              # excl all files in the root work directory in the list"
      val rule4 = "+ /work      root-dir-?/*       # incl all files under root-dir-? directories"
      val rule5 = "+ /work/root **/child-*[a-c]/a  # incl all 'a' files under 'child-' directories ending in a, b or c"
      val rule6 = "- /work/root parent-0/**        # excl all files under the 'parent-0' directory"
      val rule7 = "- /work/root **/q               # excl all 'q' files under root and its subdirectories"

      val azRangeSize = ('a' to 'z').size
      val zeroOneListSize = Seq('0', '1').size
      val rootDirsFiles = objects.rootDirs * objects.filesPerDir
      val acChildFiles = objects.nestedParentDirs * ('a' to 'c').size * Seq('a').size
      val qFiles = objects.nestedDirs * Seq('q').size

      val work = 1
      val workRoot = 1
      val rootDirs = objects.rootDirs
      val acChildDirs = objects.nestedParentDirs * ('a' to 'c').size + objects.nestedParentDirs
      val parent0Dirs = objects.nestedChildDirsPerParent

      val rules = Seq(
        rule1 -> RuleExpectation(excluded = 0, included = objects.filesPerDir + work, root = work),
        rule2 -> RuleExpectation(excluded = azRangeSize, included = 0, root = 0),
        rule3 -> RuleExpectation(excluded = zeroOneListSize, included = 0, root = 0),
        rule4 -> RuleExpectation(excluded = 0, included = rootDirsFiles + rootDirs + workRoot, root = workRoot + rootDirs),
        rule5 -> RuleExpectation(excluded = 0, included = acChildFiles + acChildDirs + workRoot, root = workRoot + acChildDirs),
        rule6 -> RuleExpectation(excluded = parent0Dirs, included = 0, root = 0),
        rule7 -> RuleExpectation(excluded = qFiles, included = 0, root = 0)
      ).zipWithIndex.map { case ((rule, expectations), lineNumber) =>
        (Rule(line = rule, lineNumber = lineNumber).get, expectations)
      }

      rules.foreach { case (rule, expectation) =>
        val matchesIncluded = new AtomicInteger(0)

        val spec = Specification(
          rules = Seq(rule),
          onMatchIncluded = _ => matchesIncluded.incrementAndGet(),
          filesystem = filesystem
        )(ec).await

        withClue(s"Specification for rule [${rule.original.line}] on line [${rule.original.lineNumber}]") {
          spec.excluded.size should be(expectation.excluded)
          spec.included.size should be(expectation.included)

          matchesIncluded.get should be(
            expectation.included - expectation.root
          ) // root directories are not included in the matches
        }
      }

      Specification(rules = rules.map(_._1), onMatchIncluded = _ => (), filesystem = filesystem)(ec).map { spec =>
        spec.unmatched should be(Seq.empty)
        spec.entries.size should be < objects.total

        val includedFromRoot = objects.filesPerDir // rule 1
        val excludedFromRoot = azRangeSize + zeroOneListSize // rule 2 + rule 3

        val includedUnderRootDirs = rootDirsFiles + rootDirs // rule 4
        val includedUnderChildDirs = acChildFiles + acChildDirs // rule 5
        val excludedUnderParent0 = parent0Dirs // rule 6
        val excludedQFiles = qFiles // rule 7

        val overlappingQFilesInParent0 = objects.nestedChildDirsPerParent
        val overlappingAcChildFilesInParent0 = includedUnderChildDirs / objects.nestedParentDirs
        val overlappingEntriesInParent0 = overlappingQFilesInParent0 + overlappingAcChildFilesInParent0

        val entriesUnderRoot = includedFromRoot + work
        val entriesUnderRootDirs = includedUnderRootDirs + workRoot
        val entriesUnderNestedDirs = includedUnderChildDirs + excludedUnderParent0 + excludedQFiles - overlappingEntriesInParent0

        val totalEntries = entriesUnderRoot + entriesUnderRootDirs + entriesUnderNestedDirs
        val excludedEntries = excludedFromRoot + excludedUnderParent0 + excludedQFiles - overlappingQFilesInParent0
        val includedEntries = totalEntries - excludedEntries

        (spec.included ++ spec.excluded).distinct.size should be(totalEntries)
        spec.excluded.size should be(excludedEntries)
        spec.included.size should be(includedEntries)
      }
    }

    it should "provide list of unmatched rules" in {
      val (filesystem, _) = createMockFileSystem(setup = FileSystemSetup.empty)

      val rule1 = Rule(line = "+ /test/ **                # include all files in directory", lineNumber = 0).get
      val rule2 = Rule(line = "+ /work  missing-test-file # include specific file", lineNumber = 0).get

      Specification(rules = Seq(rule1, rule2), onMatchIncluded = _ => (), filesystem = filesystem)(ec).map { spec =>
        spec.unmatched.toList match {
          case (`rule1`, e1) :: (`rule2`, e2) :: Nil =>
            e1 shouldBe a[NoSuchFileException]
            e2 shouldBe a[RuleMatchingFailure]

          case other =>
            fail(s"Unexpected result received: [$other]")
        }

        spec.entries shouldBe empty
      }
    }

    it should "provide a reason for including/excluding each file" in {
      val (filesystem, objects) = createMockFileSystem(
        setup = setup.copy(chars = FileSystemSetup.Chars.AlphaNumeric, nestedParentDirs = 0)
      )

      val rule1 = Rule("+ /work      ?      # incl all files in the root work directory", 0).get
      val rule2 = Rule("- /work      a      # excl file 'a'", 0).get
      val rule3 = Rule("- /work      b      # excl file 'b'", 0).get
      val rule4 = Rule("- /work      c      # excl file 'c'", 0).get
      val rule5 = Rule("+ /work      [c-f]  # incl files 'c' to 'f'", 0).get

      val rules = Seq(rule1, rule2, rule3, rule4, rule5)

      Specification(rules = rules, onMatchIncluded = _ => (), filesystem = filesystem)(ec).map { spec =>
        spec.unmatched should be(Seq.empty)
        spec.entries.size should be(objects.filesPerDir)

        val files = List(
          spec.entries.get(filesystem.getPath("/work/a")),
          spec.entries.get(filesystem.getPath("/work/b")),
          spec.entries.get(filesystem.getPath("/work/c")),
          spec.entries.get(filesystem.getPath("/work/d")),
          spec.entries.get(filesystem.getPath("/work/e")),
          spec.entries.get(filesystem.getPath("/work/f"))
        ).flatten

        files match {
          case fileA :: fileB :: fileC :: fileD :: fileE :: fileF :: Nil =>
            fileA.operation should be(Rule.Operation.Exclude)
            fileA.reason should be(
              Seq(
                Specification.Entry.Explanation(operation = Rule.Operation.Include, original = rule1.original),
                Specification.Entry.Explanation(operation = Rule.Operation.Exclude, original = rule2.original)
              )
            )

            fileB.operation should be(Rule.Operation.Exclude)
            fileB.reason should be(
              Seq(
                Specification.Entry.Explanation(operation = Rule.Operation.Include, original = rule1.original),
                Specification.Entry.Explanation(operation = Rule.Operation.Exclude, original = rule3.original)
              )
            )

            fileC.operation should be(Rule.Operation.Include)
            fileC.reason should be(
              Seq(
                Specification.Entry.Explanation(operation = Rule.Operation.Include, original = rule1.original),
                Specification.Entry.Explanation(operation = Rule.Operation.Exclude, original = rule4.original),
                Specification.Entry.Explanation(operation = Rule.Operation.Include, original = rule5.original)
              )
            )

            fileD.operation should be(Rule.Operation.Include)
            fileD.reason should be(
              Seq(
                Specification.Entry.Explanation(operation = Rule.Operation.Include, original = rule1.original),
                Specification.Entry.Explanation(operation = Rule.Operation.Include, original = rule5.original)
              )
            )

            fileE.operation should be(Rule.Operation.Include)
            fileE.reason should be(
              Seq(
                Specification.Entry.Explanation(operation = Rule.Operation.Include, original = rule1.original),
                Specification.Entry.Explanation(operation = Rule.Operation.Include, original = rule5.original)
              )
            )

            fileF.operation should be(Rule.Operation.Include)
            fileF.reason should be(
              Seq(
                Specification.Entry.Explanation(operation = Rule.Operation.Include, original = rule1.original),
                Specification.Entry.Explanation(operation = Rule.Operation.Include, original = rule5.original)
              )
            )
          case other =>
            fail(s"Unexpected result received: [$other]")
        }
      }
    }

    it should "create an empty spec if no rules are provided" in {
      Specification(rules = Seq.empty, onMatchIncluded = _ => ())(ec).map { spec =>
        spec should be(Specification.empty)
      }
    }

    it should "handle matching failures" in {
      val (filesystem, _) = createMockFileSystem(setup = FileSystemSetup.empty)

      val rule1 = Rule("+ /work/missing-dir *", 0).get

      Specification(rules = Seq(rule1), onMatchIncluded = _ => (), filesystem = filesystem)(ec).map { spec =>
        spec.unmatched.toList match {
          case (`rule1`, e) :: Nil => e shouldBe a[NoSuchFileException]
          case other               => fail(s"Unexpected result received: [$other]")
        }

        spec.entries shouldBe empty
      }
    }

    it should "support collecting parent directories" in {
      val (filesystem, _) = createMockFileSystem(setup)

      Specification
        .collectRelativeParents(
          from = filesystem.getPath("/"),
          to = filesystem.getPath("/work/root/parent-0/child-dir-a/a")
        )
        .sorted should be(
        Seq(
          "/",
          "/work",
          "/work/root",
          "/work/root/parent-0",
          "/work/root/parent-0/child-dir-a"
        ).map(filesystem.getPath(_))
      )

      Specification
        .collectRelativeParents(
          from = filesystem.getPath("/work/root/parent-0"),
          to = filesystem.getPath("/work/root/parent-0/child-dir-a/a")
        )
        .sorted should be(
        Seq(
          "/work/root/parent-0",
          "/work/root/parent-0/child-dir-a"
        ).map(filesystem.getPath(_))
      )

      Specification
        .collectRelativeParents(
          from = filesystem.getPath("/work/root/parent-0/child-dir-a"),
          to = filesystem.getPath("/work/root/parent-0/child-dir-a/a")
        )
        .sorted should be(
        Seq(
          "/work/root/parent-0/child-dir-a"
        ).map(filesystem.getPath(_))
      )

      Specification
        .collectRelativeParents(
          from = filesystem.getPath("/work/root/parent-0/child-dir-a"),
          to = filesystem.getPath("/work/root/parent-0/child-dir-a")
        )
        .sorted should be(Seq.empty)
    }

    it should "handle mismatched parent directories" in {
      val (filesystem, _) = createMockFileSystem(setup)

      Specification.collectRelativeParents(
        from = filesystem.getPath("/work/root/parent-0"),
        to = filesystem.getPath("/work/root/parent-1/child-dir-b/c")
      ) should be(Seq.empty)
    }
  }
}
