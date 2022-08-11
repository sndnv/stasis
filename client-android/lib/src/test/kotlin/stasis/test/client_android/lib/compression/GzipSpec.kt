package stasis.test.client_android.lib.compression

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe
import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import okio.buffer
import stasis.client_android.lib.compression.Gzip

class GzipSpec : WordSpec({
    "A Gzip encoder/decoder implementation" should {
        val decompressedData = "some-decompressed-data"
        val compressedData11 = "H4sIAAAAAAAAACrOz03VTUlNzs8tKEotLk5N0U1JLEkEAAAA//8DAG894xUWAAAA" // jdk11
        val compressedData17 = "H4sIAAAAAAAA/yrOz03VTUlNzs8tKEotLk5N0U1JLEkEAAAA//8DAG894xUWAAAA" // jdk17

        "provide its name" {
            Gzip.name() shouldBe ("gzip")
        }

        "compress data" {
            val source = Buffer().write(decompressedData.toByteArray())

            val actualCompressedData = Gzip.compress(source).buffer().use {
                it.readByteString()
            }

            actualCompressedData.base64() shouldBeIn (listOf(compressedData11, compressedData17))
        }

        "decompress data" {
            val source11 = Buffer().write(compressedData11.decodeBase64()!!)
            val source17 = Buffer().write(compressedData17.decodeBase64()!!)

            val actualDecompressedData11 = Gzip.decompress(source11).buffer().readUtf8()
            actualDecompressedData11 shouldBe (decompressedData)

            val actualDecompressedData17 = Gzip.decompress(source17).buffer().readUtf8()
            actualDecompressedData17 shouldBe (decompressedData)
        }
    }
})
