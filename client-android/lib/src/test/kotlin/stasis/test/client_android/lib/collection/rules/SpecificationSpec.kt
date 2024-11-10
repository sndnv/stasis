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
import java.util.concurrent.atomic.AtomicInteger

class SpecificationSpec : WordSpec({
    "A Specification" should {
        val setup = FileSystemSetup.Unix

        "support creation based on rules" {
            val (filesystem, objects) = createMockFileSystem(setup)

            objects.filesPerDir shouldBeGreaterThan (0)
            objects.rootDirs shouldBeGreaterThan (0)
            objects.nestedDirs shouldBeGreaterThan (0)

            val rule1 = Rule(
                id = 1,
                operation = Rule.Operation.Include,
                directory = "/work",
                pattern = "?",
                definition = null
            )

            val rule2 = Rule(
                id = 2,
                operation = Rule.Operation.Exclude,
                directory = "/work",
                pattern = "[a-z]",
                definition = null
            )

            val rule3 = Rule(
                id = 3,
                operation = Rule.Operation.Exclude,
                directory = "/work",
                pattern = "{0|1}",
                definition = null
            )

            val rule4 = Rule(
                id = 4,
                operation = Rule.Operation.Include,
                directory = "/work",
                pattern = "root-dir-?/*",
                definition = null
            )

            val rule5 = Rule(
                id = 5,
                operation = Rule.Operation.Include,
                directory = "/work/root",
                pattern = "**/child-*[a-c]/a",
                definition = null
            )

            val rule6 = Rule(
                id = 6,
                operation = Rule.Operation.Exclude,
                directory = "/work/root",
                pattern = "parent-0/**",
                definition = null
            )

            val rule7 = Rule(
                id = 7,
                operation = Rule.Operation.Exclude,
                directory = "/work/root",
                pattern = "**/q",
                definition = null
            )

            val azRangeSize = ('a'..'z').toList().size
            val zeroOneListSize = listOf('0', '1').size
            val rootDirsFiles = objects.rootDirs * objects.filesPerDir
            val acChildFiles = objects.nestedParentDirs * ('a'..'c').toList().size * listOf('a').size
            val qFiles = objects.nestedDirs * listOf('q').size

            val work = 1
            val workRoot = 1
            val rootDirs = objects.rootDirs
            val acChildDirs = objects.nestedParentDirs * ('a'..'c').toList().size + objects.nestedParentDirs
            val parent0Dirs = objects.nestedChildDirsPerParent

            val rules = listOf(
                rule1 to RuleExpectation(excluded = 0, included = objects.filesPerDir + work, root = work),
                rule2 to RuleExpectation(excluded = azRangeSize, included = 0, root = 0),
                rule3 to RuleExpectation(excluded = zeroOneListSize, included = 0, root = 0),
                rule4 to RuleExpectation(
                    excluded = 0,
                    included = rootDirsFiles + rootDirs + workRoot,
                    root = workRoot + rootDirs
                ),
                rule5 to RuleExpectation(
                    excluded = 0,
                    included = acChildFiles + acChildDirs + workRoot,
                    root = workRoot + acChildDirs
                ),
                rule6 to RuleExpectation(excluded = parent0Dirs, included = 0, root = 0),
                rule7 to RuleExpectation(excluded = qFiles, included = 0, root = 0)
            )

            rules.forEach {
                val (rule, expectation) = it
                val matchesIncluded = AtomicInteger(0)

                withClue("Specification for rule [${rule.id}]: [${rule.operation} ${rule.directory} ${rule.pattern}]") {
                    val spec = Specification(listOf(rule), { matchesIncluded.incrementAndGet() }, filesystem)
                    spec.excluded.size shouldBe (expectation.excluded)
                    spec.included.size shouldBe (expectation.included)

                    // root directories are not included in the matches
                    matchesIncluded.get() shouldBe (expectation.included - expectation.root)
                }
            }

            val spec = Specification(rules = rules.map { it.first }, onMatchIncluded = {}, filesystem = filesystem)

            spec.unmatched shouldBe (emptyList())
            spec.entries.size shouldBeLessThan (objects.total)

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

            val rule1 = Rule(
                id = 1,
                operation = Rule.Operation.Include,
                directory = "/test/",
                pattern = "**",
                definition = null
            )

            val rule2 = Rule(
                id = 2,
                operation = Rule.Operation.Include,
                directory = "/work",
                pattern = "missing-test-file",
                definition = null
            )

            val spec = Specification(listOf(rule1, rule2), {}, filesystem)

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

            val rule1 = Rule(
                id = 1,
                operation = Rule.Operation.Include,
                directory = "/work",
                pattern = "?",
                definition = null
            )

            val rule2 = Rule(
                id = 2,
                operation = Rule.Operation.Exclude,
                directory = "/work",
                pattern = "a",
                definition = null
            )

            val rule3 = Rule(
                id = 3,
                operation = Rule.Operation.Exclude,
                directory = "/work",
                pattern = "b",
                definition = null
            )

            val rule4 = Rule(
                id = 4,
                operation = Rule.Operation.Exclude,
                directory = "/work",
                pattern = "c",
                definition = null
            )

            val rule5 = Rule(
                id = 5,
                operation = Rule.Operation.Include,
                directory = "/work",
                pattern = "[c-f]",
                definition = null
            )

            val rules = listOf(rule1, rule2, rule3, rule4, rule5)

            val spec = Specification(rules = rules, onMatchIncluded = {}, filesystem = filesystem)

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
                        Specification.Entry.Explanation(operation = Rule.Operation.Include),
                        Specification.Entry.Explanation(operation = Rule.Operation.Exclude)
                    )
                    )

            fileB.operation shouldBe (Rule.Operation.Exclude)
            fileB.reason shouldBe (
                    listOf(
                        Specification.Entry.Explanation(operation = Rule.Operation.Include),
                        Specification.Entry.Explanation(operation = Rule.Operation.Exclude)
                    )
                    )

            fileC.operation shouldBe (Rule.Operation.Include)
            fileC.reason shouldBe (
                    listOf(
                        Specification.Entry.Explanation(operation = Rule.Operation.Include),
                        Specification.Entry.Explanation(operation = Rule.Operation.Exclude),
                        Specification.Entry.Explanation(operation = Rule.Operation.Include)
                    )
                    )

            fileD.operation shouldBe (Rule.Operation.Include)
            fileD.reason shouldBe (
                    listOf(
                        Specification.Entry.Explanation(operation = Rule.Operation.Include),
                        Specification.Entry.Explanation(operation = Rule.Operation.Include)
                    )
                    )

            fileE.operation shouldBe (Rule.Operation.Include)
            fileE.reason shouldBe (
                    listOf(
                        Specification.Entry.Explanation(operation = Rule.Operation.Include),
                        Specification.Entry.Explanation(operation = Rule.Operation.Include)
                    )
                    )

            fileF.operation shouldBe (Rule.Operation.Include)
            fileF.reason shouldBe (
                    listOf(
                        Specification.Entry.Explanation(operation = Rule.Operation.Include),
                        Specification.Entry.Explanation(operation = Rule.Operation.Include)
                    )
                    )
        }

        "create an empty spec if no rules are provided" {
            Specification(rules = emptyList(), onMatchIncluded = {}) shouldBe (Specification.empty())
        }

        "handle matching failures" {
            val (filesystem, _) = createMockFileSystem(setup = FileSystemSetup.empty())

            val rule1 = Rule(
                id = 1,
                operation = Rule.Operation.Include,
                directory = "/work/missing-dir",
                pattern = "*",
                definition = null
            )

            val spec = Specification(rules = listOf(rule1), onMatchIncluded = {}, filesystem = filesystem)

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
