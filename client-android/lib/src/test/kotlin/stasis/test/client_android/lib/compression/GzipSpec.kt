package stasis.test.client_android.lib.compression

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import okio.buffer
import stasis.client_android.lib.compression.Gzip

class GzipSpec : WordSpec({
    "A Gzip encoder/decoder implementation" should {
        val decompressedData = "some-decompressed-data"
        val compressedData = "H4sIAAAAAAAAACrOz03VTUlNzs8tKEotLk5N0U1JLEkEAAAA//8DAG894xUWAAAA"

        "compress data" {
            val source = Buffer().write(decompressedData.toByteArray())

            val actualCompressedData = Gzip.compress(source).buffer().use {
                it.readByteString()
            }

            actualCompressedData.base64() shouldBe (compressedData)
        }

        "decompress data" {
            val source = Buffer().write(compressedData.decodeBase64()!!)

            val actualDecompressedData = Gzip.decompress(source).buffer().readUtf8()
            actualDecompressedData shouldBe (decompressedData)
        }
    }
})
