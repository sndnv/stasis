package stasis.test.client_android.lib.analysis

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.analysis.Checksum
import stasis.test.client_android.lib.ResourceHelpers.asTestResource
import java.math.BigInteger

class ChecksumSpec : WordSpec({
    "A Checksum implementation" should {
        val sourceFile = "/analysis/digest-source-file".asTestResource()

        "calculate digest checksums for files" {
            val expectedChecksum = BigInteger(
                "39848954327861382298906397279462496107584551024072291" +
                        "193471648171519709574703666208888992159541063683939" +
                        "25455196856231941899873364258769048659015726835952"
            )

            val actualChecksum = Checksum.digest(file = sourceFile, algorithm = "SHA-512")
            actualChecksum shouldBe (expectedChecksum)
        }

        "calculate CRC32 checksums for files" {
            val expectedChecksum = BigInteger("595309308")

            val actualChecksum = Checksum.Companion.CRC32.calculate(file = sourceFile)
            actualChecksum shouldBe (expectedChecksum)
        }

        "calculate MD5 checksums for files" {
            val expectedChecksum = BigInteger("124476216797902834426689518600270260549")

            val actualChecksum = Checksum.Companion.MD5.calculate(file = sourceFile)
            actualChecksum shouldBe (expectedChecksum)
        }

        "calculate SHA1 checksums for files" {
            val expectedChecksum = BigInteger("545568869381376109390570303274177429634814154141")

            val actualChecksum = Checksum.Companion.SHA1.calculate(file = sourceFile)
            actualChecksum shouldBe (expectedChecksum)
        }

        "calculate SHA256 checksums for files" {
            val expectedChecksum = BigInteger("96075381802863146919837723321013254207026023090442774587942108314962662306148")

            val actualChecksum = Checksum.Companion.SHA256.calculate(file = sourceFile)
            actualChecksum shouldBe (expectedChecksum)
        }

        "provide checksum implementations based on config" {
            Checksum.apply(checksum = "crc32") shouldBe (Checksum.Companion.CRC32)
            Checksum.apply(checksum = "md5") shouldBe (Checksum.Companion.MD5)
            Checksum.apply(checksum = "sha1") shouldBe (Checksum.Companion.SHA1)
            Checksum.apply(checksum = "sha256") shouldBe (Checksum.Companion.SHA256)
        }
    }
})
