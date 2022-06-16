package stasis.test.client_android.lib.ops.recovery.stages.internal

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okio.Buffer
import okio.Source
import okio.buffer
import stasis.client_android.lib.ops.recovery.stages.internal.DecompressedSource.decompress
import stasis.test.client_android.lib.mocks.MockCompression

class DecompressedSourceSpec : WordSpec({
    "A DecompressedByteStringSource" should {
        "support data stream decompression" {
            val compression = object : MockCompression() {
                override fun decompress(source: Source): Source =
                    Buffer().writeUtf8("decompressed")
            }

            val original = Buffer().write("original".toByteArray())

            val decompressed = original.decompress(compression)
            decompressed.buffer().readUtf8() shouldBe ("decompressed")
        }
    }
})
