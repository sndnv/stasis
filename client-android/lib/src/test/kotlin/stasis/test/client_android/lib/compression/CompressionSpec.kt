package stasis.test.client_android.lib.compression

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.compression.Compression
import stasis.client_android.lib.compression.Deflate
import stasis.client_android.lib.compression.Gzip

class CompressionSpec : WordSpec({
    "Compression" should {
        "provide encoder/decoder implementations based on config" {
            Compression(compression = "deflate") shouldBe (Deflate)
            Compression(compression = "gzip") shouldBe (Gzip)

            val e = shouldThrow<IllegalArgumentException> {
                Compression(compression = "other")
            }
            e.message shouldBe ("Unsupported compression provided: [other]")
        }
    }
})
