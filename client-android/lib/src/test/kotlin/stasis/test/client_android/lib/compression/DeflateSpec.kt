package stasis.test.client_android.lib.compression

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import okio.buffer
import stasis.client_android.lib.compression.Deflate

class DeflateSpec : WordSpec({
    "A Deflate encoder/decoder implementation" should {
        val decompressedData = "some-decompressed-data"
        val compressedData = "eNoqzs9N1U1JTc7PLShKLS5OTdFNSSxJBAAAAP//AwBkTwin"

        "compress data" {
            val source = Buffer().write(decompressedData.toByteArray())

            val actualCompressedData = Deflate.compress(source).buffer().use {
                it.readByteString()
            }

            actualCompressedData.base64() shouldBe (compressedData)
        }

        "decompress data" {
            val source = Buffer().write(compressedData.decodeBase64()!!)

            val actualDecompressedData = Deflate.decompress(source).buffer().readUtf8()
            actualDecompressedData shouldBe (decompressedData)
        }
    }
})
