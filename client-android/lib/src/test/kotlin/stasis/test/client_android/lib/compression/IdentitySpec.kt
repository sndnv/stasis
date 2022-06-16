package stasis.test.client_android.lib.compression

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import okio.buffer
import stasis.client_android.lib.compression.Identity

class IdentitySpec : WordSpec({
    "An Identity encoder/decoder implementation" should {
        val data = "some-data"

        "provide its name" {
            Identity.name() shouldBe ("none")
        }

        "skip compression" {
            val source = Buffer().write(data.toByteArray())

            val actualCompressedData = Identity.compress(source).buffer().use {
                it.readByteString()
            }

            actualCompressedData.utf8() shouldBe (data)
        }

        "skip decompression" {
            val source = Buffer().write(data.toByteArray())

            val actualDecompressedData = Identity.decompress(source).buffer().readUtf8()
            actualDecompressedData shouldBe (data)
        }
    }
})
