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
            val original: List<Triple<Int, Path, suspend () -> Source>> = listOf(
                Triple(0, Paths.get("/tmp/file/one__part=0"), suspend { Buffer().write("original_1".toByteArray()) })
            )

            val merged = original.merged().buffer().readUtf8()

            merged shouldBe ("original_1")
        }

        "support data stream merging (multiple crates)" {
            val original: List<Triple<Int, Path, suspend () -> Source>> = listOf(
                Triple(0, Paths.get("/tmp/file/one__part=0"), suspend { Buffer().write("original_1".toByteArray()) }),
                Triple(2, Paths.get("/tmp/file/one__part=2"), suspend { Buffer().write("original_3".toByteArray()) }),
                Triple(1, Paths.get("/tmp/file/one__part=1"), suspend { Buffer().write("original_2".toByteArray()) })
            )

            val merged = original.merged().buffer().readUtf8()

            merged shouldBe ("original_1original_2original_3")
        }

        "fail if no crates are provided" {
            val original: List<Triple<Int, Path, suspend () -> Source>> = emptyList()

            val e = shouldThrow<IllegalArgumentException> {
                original.merged()
            }

            e.message shouldBe ("Expected at least one crate but none were found")
        }
    }
})
