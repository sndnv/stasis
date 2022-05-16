package stasis.test.client_android.lib.collection.rules.internal

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.collection.rules.internal.FilesWalker
import stasis.test.client_android.lib.ResourceHelpers.FileSystemSetup
import stasis.test.client_android.lib.ResourceHelpers.createMockFileSystem
import java.nio.file.Path

class FilesWalkerSpec : WordSpec({
    "A FilesWalker FilterResult" should {
        "support checking if a result is empty" {
            val empty = FilesWalker.FilterResult(matches = emptyMap(), failures = emptyMap())

            empty.isEmpty() shouldBe (true)
        }
    }

    "A FilesWalker" should {
        val setup = FileSystemSetup.Unix

        "support filtering files and directories based on provided matchers" {
            val (filesystem, _) = createMockFileSystem(setup)

            val rule1 = Rule(id = 0, operation = Rule.Operation.Include, directory = "/", pattern = "*")
            val matcher1 = filesystem.getPathMatcher("glob:/work/root/parent-*/*-{a,b,c}")

            val rule2 = Rule(id = 1, operation = Rule.Operation.Exclude, directory = "/", pattern = "*")
            val matcher2 = filesystem.getPathMatcher("glob:/work/root/parent-*/*-{d,e}")

            val matchers = listOf(
                rule1 to matcher1,
                rule2 to matcher2
            )

            val matchedSuccessful = mutableListOf<Path>()
            val successfulResult = FilesWalker.filter(
                start = filesystem.getPath("/work/root/parent-1"),
                onMatchIncluded = { matchedSuccessful.add(it) },
                matchers = matchers
            )

            matchedSuccessful.map { it.toString() } shouldBe (listOf(
                "/work/root/parent-1/child-dir-a",
                "/work/root/parent-1/child-dir-b",
                "/work/root/parent-1/child-dir-c"
            ))

            successfulResult.isEmpty() shouldBe (false)

            successfulResult.matches.map { (k, v) ->
                k.asString() to v.map { it.toString() }
            }.toList() shouldBe (listOf(
                "+ / *" to listOf(
                    "/work/root/parent-1/child-dir-a",
                    "/work/root/parent-1/child-dir-b",
                    "/work/root/parent-1/child-dir-c"
                ),
                "- / *" to listOf(
                    "/work/root/parent-1/child-dir-d",
                    "/work/root/parent-1/child-dir-e"
                )
            ))

            successfulResult.failures.size shouldBe (0)

            val matchedFailed = mutableListOf<Path>()
            val failedResult = FilesWalker.filter(
                start = filesystem.getPath("/work/root/other"),
                onMatchIncluded = { matchedFailed.add(it) },
                matchers = matchers
            )

            matchedFailed.size shouldBe (0)
            failedResult.isEmpty() shouldBe (false)
            failedResult.matches.size shouldBe (0)

            failedResult.failures.map { (k, v) -> k.toString() to v.toString() }.toList() shouldBe (listOf(
                "/work/root/other" to "java.nio.file.NoSuchFileException: /work/root/other"
            ))
        }

        "support skipping excluded subtrees" {
            val (filesystem, _) = createMockFileSystem(setup)

            val rule1 = Rule(id = 0, operation = Rule.Operation.Include, directory = "/", pattern = "*")
            val matcher1 = filesystem.getPathMatcher("glob:/work/root/parent-{0,1}/*-{a,b,c}/*")

            val rule2 = Rule(id = 1, operation = Rule.Operation.Exclude, directory = "/", pattern = "*")
            val matcher2 = filesystem.getPathMatcher("glob:/work/root/parent-{0,1}/*-{c,d,e}")

            val matchers = listOf(
                rule1 to matcher1,
                rule2 to matcher2
            )

            val result = FilesWalker.filter(
                start = filesystem.getPath("/work/root"),
                onMatchIncluded = {},
                matchers = matchers
            )

            result.isEmpty() shouldBe (false)
            result.failures.size shouldBe (0)

            val (included, excluded) = result.matches.toList()
                .partition { it.first.operation == Rule.Operation.Include }

            included.flatMap { it.second }.map { it.toString() }.forEach { path ->
                path shouldNotContain "child-dir-c"
                path shouldNotContain "child-dir-d"
                path shouldNotContain "child-dir-e"
            }

            excluded.flatMap { it.second }.map { it.toString() } shouldBe (listOf(
                "/work/root/parent-0/child-dir-c",
                "/work/root/parent-0/child-dir-d",
                "/work/root/parent-0/child-dir-e",
                "/work/root/parent-1/child-dir-c",
                "/work/root/parent-1/child-dir-d",
                "/work/root/parent-1/child-dir-e"
            ))
        }
    }
})
