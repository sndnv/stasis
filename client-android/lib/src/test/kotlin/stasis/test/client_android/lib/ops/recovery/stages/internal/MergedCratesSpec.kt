package stasis.test.client_android.lib.ops.recovery.stages.internal

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okio.Buffer
import okio.Source
import okio.buffer
import stasis.client_android.lib.ops.recovery.stages.internal.MergedCrates.merged
import java.nio.file.Path
import java.nio.file.Paths

class MergedCratesSpec : WordSpec({
    "MergedCrates" should {
        "support data stream merging (single crate)" {
            val original: List<Pair<Path, Source>> = listOf(
                Pair(Paths.get("/tmp/file/one_0"), Buffer().write("original_1".toByteArray()))
            )

            val merged = original.merged().buffer().readUtf8()

            merged shouldBe ("original_1")
        }

        "support data stream merging (multiple crates)" {
            val original: List<Pair<Path, Source>> = listOf(
                Pair(Paths.get("/tmp/file/one_0"), Buffer().write("original_1".toByteArray())),
                Pair(Paths.get("/tmp/file/one_2"), Buffer().write("original_3".toByteArray())),
                Pair(Paths.get("/tmp/file/one_1"), Buffer().write("original_2".toByteArray()))
            )

            val merged = original.merged().buffer().readUtf8()

            merged shouldBe ("original_1original_2original_3")
        }

        "fail if no crates are provided" {
            val original: List<Pair<Path, Source>> = emptyList()

            val e = shouldThrow<IllegalArgumentException> {
                original.merged()
            }

            e.message shouldBe ("Expected at least one crate but none were found")
        }
    }
})
