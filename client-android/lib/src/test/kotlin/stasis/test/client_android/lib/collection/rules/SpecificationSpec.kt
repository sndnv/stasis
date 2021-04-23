package stasis.test.client_android.lib.collection.rules

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.collection.rules.Specification
import stasis.client_android.lib.collection.rules.exceptions.RuleMatchingFailure
import stasis.test.client_android.lib.ResourceHelpers.FileSystemSetup
import stasis.test.client_android.lib.ResourceHelpers.RuleExpectation
import stasis.test.client_android.lib.ResourceHelpers.createMockFileSystem

class SpecificationSpec : WordSpec({
    "A Specification" should {
        val setup = FileSystemSetup.Unix

        "support creation based on rules" {
            val (filesystem, objects) = createMockFileSystem(setup)

            objects.filesPerDir shouldBeGreaterThan (0)
            objects.rootDirs shouldBeGreaterThan (0)
            objects.nestedDirs shouldBeGreaterThan (0)

            val rule1 = "+ /work      ?                  # incl all files in the root work directory"
            val rule2 = "- /work      [a-z]              # excl all files in the root work directory in the range"
            val rule3 = "- /work      {0|1}              # excl all files in the root work directory in the list"
            val rule4 = "+ /work      root-dir-?/*       # incl all files under root-dir-? directories"
            val rule5 = "+ /work/root **/child-*[a-c]/a  # incl all 'a' files under 'child-' directories ending in a, b or c"
            val rule6 = "- /work/root parent-0/**        # excl all files under the 'parent-0' directory"
            val rule7 = "- /work/root **/q               # excl all 'q' files under root and its subdirectories"

            val azRangeSize = ('a'..'z').toList().size
            val zeroOneListSize = listOf('0', '1').size
            val rootDirsFiles = objects.rootDirs * objects.filesPerDir
            val acChildFiles = objects.nestedParentDirs * ('a'..'c').toList().size * listOf('a').size
            val parent0Files = objects.nestedChildDirsPerParent * objects.filesPerDir
            val qFiles = objects.nestedDirs * listOf('q').size

            val work = 1
            val workRoot = 1
            val rootDirs = objects.rootDirs
            val acChildDirs = objects.nestedParentDirs * ('a'..'c').toList().size + objects.nestedParentDirs
            val parent0Dirs = objects.nestedChildDirsPerParent

            val rules = listOf(
                rule1 to RuleExpectation(excluded = 0, included = objects.filesPerDir + work),
                rule2 to RuleExpectation(excluded = azRangeSize, included = 0),
                rule3 to RuleExpectation(excluded = zeroOneListSize, included = 0),
                rule4 to RuleExpectation(excluded = 0, included = rootDirsFiles + rootDirs + workRoot),
                rule5 to RuleExpectation(excluded = 0, included = acChildFiles + acChildDirs + workRoot),
                rule6 to RuleExpectation(excluded = parent0Files + parent0Dirs, included = 0),
                rule7 to RuleExpectation(excluded = qFiles, included = 0)
            ).withIndex().map {
                val (rule, expectations) = it.value
                val lineNumber = it.index
                Pair(Rule(line = rule, lineNumber = lineNumber).get(), expectations)
            }

            rules.forEach {
                val (rule, expectation) = it

                withClue("Specification for rule [${rule.original.line}] on line [${rule.original.lineNumber}]") {
                    val spec = Specification(listOf(rule), filesystem)
                    spec.excluded.size shouldBe (expectation.excluded)
                    spec.included.size shouldBe (expectation.included)
                }
            }

            val spec = Specification(rules = rules.map { it.first }, filesystem = filesystem)

            spec.unmatched shouldBe (emptyList())
            spec.entries.size shouldBeLessThan (objects.total)

            val includedFromRoot = objects.filesPerDir // rule 1
            val excludedFromRoot = azRangeSize + zeroOneListSize // rule 2 + rule 3

            val includedUnderRootDirs = rootDirsFiles + rootDirs // rule 4
            val includedUnderChildDirs = acChildFiles + acChildDirs // rule 5
            val excludedUnderParent0 = parent0Files + parent0Dirs // rule 6
            val excludedQFiles = qFiles // rule 7

            val overlappingQFilesInParent0 = objects.nestedChildDirsPerParent
            val overlappingAcChildFilesInParent0 = includedUnderChildDirs / objects.nestedParentDirs
            val overlappingEntriesInParent0 = overlappingQFilesInParent0 + overlappingAcChildFilesInParent0

            val entriesUnderRoot = includedFromRoot + work
            val entriesUnderRootDirs = includedUnderRootDirs + workRoot
            val entriesUnderNestedDirs =
                includedUnderChildDirs + excludedUnderParent0 + excludedQFiles - overlappingEntriesInParent0

            val totalEntries = entriesUnderRoot + entriesUnderRootDirs + entriesUnderNestedDirs
            val excludedEntries = excludedFromRoot + excludedUnderParent0 + excludedQFiles - overlappingQFilesInParent0
            val includedEntries = totalEntries - excludedEntries

            (spec.included + spec.excluded).distinct().size shouldBe (totalEntries)
            spec.excluded.size shouldBe (excludedEntries)
            spec.included.size shouldBe (includedEntries)
        }

        "provide list of unmatched rules" {
            val (filesystem, _) = createMockFileSystem(setup = FileSystemSetup.empty())

            val rule1 = Rule(line = "+ /test/ **                # include all files in directory", lineNumber = 0).get()
            val rule2 = Rule(line = "+ /work  missing-test-file # include specific file", lineNumber = 0).get()

            val spec = Specification(listOf(rule1, rule2), filesystem)

            spec.unmatched.size shouldBe (2)
            val (actualRule1, e1) = spec.unmatched[0]
            val (actualRule2, e2) = spec.unmatched[1]

            actualRule1 shouldBe (rule1)
            e1::class.java shouldBe (java.nio.file.NoSuchFileException::class.java)

            actualRule2 shouldBe (rule2)
            e2::class.java shouldBe (RuleMatchingFailure::class.java)

            spec.entries.isEmpty() shouldBe (true)
        }

        "provide a reason for including/excluding each file" {
            val (filesystem, objects) = createMockFileSystem(
                setup = setup.copy(chars = FileSystemSetup.AlphaNumericChars, nestedParentDirs = 0)
            )

            val rule1 = Rule("+ /work      ?      # incl all files in the root work directory", 0).get()
            val rule2 = Rule("- /work      a      # excl file 'a'", 0).get()
            val rule3 = Rule("- /work      b      # excl file 'b'", 0).get()
            val rule4 = Rule("- /work      c      # excl file 'c'", 0).get()
            val rule5 = Rule("+ /work      [c-f]  # incl files 'c' to 'f'", 0).get()

            val rules = listOf(rule1, rule2, rule3, rule4, rule5)

            val spec = Specification(rules = rules, filesystem = filesystem)

            spec.unmatched shouldBe (emptyList())
            spec.entries.size shouldBe (objects.filesPerDir)

            val files = listOf(
                spec.entries[filesystem.getPath("/work/a")],
                spec.entries[filesystem.getPath("/work/b")],
                spec.entries[filesystem.getPath("/work/c")],
                spec.entries[filesystem.getPath("/work/d")],
                spec.entries[filesystem.getPath("/work/e")],
                spec.entries[filesystem.getPath("/work/f")]
            ).filterNotNull()

            files.size shouldBe (6)

            val fileA = files[0]
            val fileB = files[1]
            val fileC = files[2]
            val fileD = files[3]
            val fileE = files[4]
            val fileF = files[5]

            fileA.operation shouldBe (Rule.Operation.Exclude)
            fileA.reason shouldBe (
                    listOf(
                        Specification.Entry.Explanation(operation = Rule.Operation.Include, original = rule1.original),
                        Specification.Entry.Explanation(operation = Rule.Operation.Exclude, original = rule2.original)
                    )
                    )

            fileB.operation shouldBe (Rule.Operation.Exclude)
            fileB.reason shouldBe (
                    listOf(
                        Specification.Entry.Explanation(operation = Rule.Operation.Include, original = rule1.original),
                        Specification.Entry.Explanation(operation = Rule.Operation.Exclude, original = rule3.original)
                    )
                    )

            fileC.operation shouldBe (Rule.Operation.Include)
            fileC.reason shouldBe (
                    listOf(
                        Specification.Entry.Explanation(operation = Rule.Operation.Include, original = rule1.original),
                        Specification.Entry.Explanation(operation = Rule.Operation.Exclude, original = rule4.original),
                        Specification.Entry.Explanation(operation = Rule.Operation.Include, original = rule5.original)
                    )
                    )

            fileD.operation shouldBe (Rule.Operation.Include)
            fileD.reason shouldBe (
                    listOf(
                        Specification.Entry.Explanation(operation = Rule.Operation.Include, original = rule1.original),
                        Specification.Entry.Explanation(operation = Rule.Operation.Include, original = rule5.original)
                    )
                    )

            fileE.operation shouldBe (Rule.Operation.Include)
            fileE.reason shouldBe (
                    listOf(
                        Specification.Entry.Explanation(operation = Rule.Operation.Include, original = rule1.original),
                        Specification.Entry.Explanation(operation = Rule.Operation.Include, original = rule5.original)
                    )
                    )

            fileF.operation shouldBe (Rule.Operation.Include)
            fileF.reason shouldBe (
                    listOf(
                        Specification.Entry.Explanation(operation = Rule.Operation.Include, original = rule1.original),
                        Specification.Entry.Explanation(operation = Rule.Operation.Include, original = rule5.original)
                    )
                    )
        }

        "create an empty spec if no rules are provided" {
            Specification(rules = emptyList()) shouldBe (Specification.empty())
        }

        "handle matching failures" {
            val (filesystem, _) = createMockFileSystem(setup = FileSystemSetup.empty())

            val rule1 = Rule("+ /work/missing-dir *", 0).get()

            val spec = Specification(rules = listOf(rule1), filesystem = filesystem)

            spec.unmatched.size shouldBe (1)

            val (actualRule1, e1) = spec.unmatched[0]

            actualRule1 shouldBe (rule1)
            e1::class.java shouldBe (java.nio.file.NoSuchFileException::class.java)

            spec.entries.isEmpty() shouldBe (true)
        }

        "support collecting parent directories" {
            val (filesystem, _) = createMockFileSystem(setup)

            Specification.collectRelativeParents(
                from = filesystem.getPath("/"),
                to = filesystem.getPath("/work/root/parent-0/child-dir-a/a")
            ).sorted() shouldBe (
                    listOf(
                        "/",
                        "/work",
                        "/work/root",
                        "/work/root/parent-0",
                        "/work/root/parent-0/child-dir-a"
                    ).map { filesystem.getPath(it) })

            Specification.collectRelativeParents(
                from = filesystem.getPath("/work/root/parent-0"),
                to = filesystem.getPath("/work/root/parent-0/child-dir-a/a")
            ).sorted() shouldBe (
                    listOf(
                        "/work/root/parent-0",
                        "/work/root/parent-0/child-dir-a"
                    ).map { filesystem.getPath(it) })

            Specification.collectRelativeParents(
                from = filesystem.getPath("/work/root/parent-0/child-dir-a"),
                to = filesystem.getPath("/work/root/parent-0/child-dir-a/a")
            ).sorted() shouldBe (
                    listOf(
                        "/work/root/parent-0/child-dir-a"
                    ).map { filesystem.getPath(it) })

            Specification.collectRelativeParents(
                from = filesystem.getPath("/work/root/parent-0/child-dir-a"),
                to = filesystem.getPath("/work/root/parent-0/child-dir-a")
            ).sorted() shouldBe (emptyList())
        }

        "handle mismatched parent directories" {
            val (filesystem, _) = createMockFileSystem(setup)

            Specification.collectRelativeParents(
                from = filesystem.getPath("/work/root/parent-0"),
                to = filesystem.getPath("/work/root/parent-1/child-dir-b/c")
            ) shouldBe (emptyList())
        }
    }
})
