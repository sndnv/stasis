package stasis.test.specs.unit.client.collection.rules

import java.nio.file.NoSuchFileException

import stasis.client.collection.rules.{Rule, Specification}
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.client.ResourceHelpers

trait SpecificationBehaviour { _: UnitSpec with ResourceHelpers =>
  import ResourceHelpers._

  def specification(setup: FileSystemSetup): Unit = {
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
      val parent0Files = objects.nestedChildDirsPerParent * objects.filesPerDir
      val qFiles = objects.nestedDirs * Seq('q').size

      val rules = Seq(
        rule1 -> RuleExpectation(total = objects.filesPerDir, excluded = 0, included = objects.filesPerDir),
        rule2 -> RuleExpectation(total = azRangeSize, excluded = azRangeSize, included = 0),
        rule3 -> RuleExpectation(total = zeroOneListSize, excluded = zeroOneListSize, included = 0),
        rule4 -> RuleExpectation(total = rootDirsFiles, excluded = 0, included = rootDirsFiles),
        rule5 -> RuleExpectation(total = acChildFiles, excluded = 0, included = acChildFiles),
        rule6 -> RuleExpectation(total = parent0Files, excluded = parent0Files, included = 0),
        rule7 -> RuleExpectation(total = qFiles, excluded = qFiles, included = 0)
      ).zipWithIndex.map {
        case ((rule, expectations), lineNumber) => (Rule(line = rule, lineNumber = lineNumber).get, expectations)
      }

      rules.foreach {
        case (rule, expectation) =>
          val spec = Specification(Seq(rule), filesystem)
          withClue(s"Specification for rule [${rule.original.line}] on line [${rule.original.lineNumber}]") {
            spec.files.size should be(expectation.total)
            spec.excluded.size should be(expectation.excluded)
            spec.included.size should be(expectation.included)
          }
      }

      val spec = Specification(rules = rules.map(_._1), filesystem = filesystem)

      spec.unmatched should be(Seq.empty)
      spec.files.size should be < objects.total

      val includedFromRoot = objects.filesPerDir // rule 1
      val excludedFromRoot = azRangeSize + zeroOneListSize // rule 2 + rule 3

      val includedUnderRootDirs = rootDirsFiles // rule 4
      val includedUnderChildDirs = acChildFiles // rule 5
      val excludedUnderParent0 = parent0Files // rule 6
      val excludedQFiles = qFiles // rule 7

      val overlappingQFilesInParent0 = objects.nestedChildDirsPerParent
      val overlappingAcChildFilesInParent0 = includedUnderChildDirs / objects.nestedParentDirs
      val overlappingEntriesInParent0 = overlappingQFilesInParent0 + overlappingAcChildFilesInParent0

      val entriesUnderRoot = includedFromRoot
      val entriesUnderRootDirs = includedUnderRootDirs
      val entriesUnderNestedDirs = includedUnderChildDirs + excludedUnderParent0 + excludedQFiles - overlappingEntriesInParent0

      val totalEntries = entriesUnderRoot + entriesUnderRootDirs + entriesUnderNestedDirs
      val excludedFiles = excludedFromRoot + excludedUnderParent0 + excludedQFiles - overlappingQFilesInParent0
      val includedFiles = totalEntries - excludedFiles

      spec.files.size should be(totalEntries)
      spec.excluded.size should be(excludedFiles)
      spec.included.size should be(includedFiles)
    }

    it should "provide list of unmatched rules" in {
      val (filesystem, _) = createMockFileSystem(setup = FileSystemSetup.empty)

      val rules = Seq(
        Rule(line = "+ / ** # include all files", lineNumber = 0).get
      )

      val spec = Specification(rules, filesystem)

      spec.unmatched.map(_._1) should be(rules)
      spec.files shouldBe empty
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

      val spec = Specification(rules = rules, filesystem = filesystem)

      spec.unmatched should be(Seq.empty)
      spec.files.size should be(objects.filesPerDir)

      val files = List(
        spec.files.get(filesystem.getPath("/work/a")),
        spec.files.get(filesystem.getPath("/work/b")),
        spec.files.get(filesystem.getPath("/work/c")),
        spec.files.get(filesystem.getPath("/work/d")),
        spec.files.get(filesystem.getPath("/work/e")),
        spec.files.get(filesystem.getPath("/work/f"))
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

    it should "create an empty spec if no rules are provided" in {
      Specification(rules = Seq.empty) should be(Specification.empty)
    }

    it should "handle matching failures" in {
      val (filesystem, _) = createMockFileSystem(setup = FileSystemSetup.empty)

      val rule1 = Rule("+ /work/missing-dir *", 0).get

      val spec = Specification(rules = Seq(rule1), filesystem = filesystem)

      spec.unmatched.toList match {
        case (`rule1`, e) :: Nil => e shouldBe a[NoSuchFileException]
        case other               => fail(s"Unexpected result received: [$other]")
      }

      spec.files shouldBe empty
    }
  }
}
