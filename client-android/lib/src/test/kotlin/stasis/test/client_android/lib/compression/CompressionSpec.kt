package stasis.test.client_android.lib.compression

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.compression.Compression
import stasis.client_android.lib.compression.Deflate
import stasis.client_android.lib.compression.Gzip
import stasis.client_android.lib.compression.Identity
import stasis.client_android.lib.model.SourceEntity
import stasis.client_android.lib.model.TargetEntity
import stasis.test.client_android.lib.Fixtures
import java.nio.file.Paths

class CompressionSpec : WordSpec({
    "Compression" should {
        "provide new instances based on config" {
            val compression = Compression(withDefaultCompression = "gzip", withDisabledExtensions = "a, b, c")

            compression.defaultCompression() shouldBe (Gzip)
            compression.disabledExtensions() shouldBe (setOf("a", "b", "c"))
        }

        "provide encoder/decoder implementations based on config" {
            Compression.fromString(compression = "deflate") shouldBe (Deflate)
            Compression.fromString(compression = "gzip") shouldBe (Gzip)
            Compression.fromString(compression = "none") shouldBe (Identity)

            val e = shouldThrow<IllegalArgumentException> {
                Compression.fromString(compression = "other")
            }

            e.message shouldBe ("Unsupported compression provided: [other]")
        }

        "determine compression algorithms based on entity path" {
            val compression = Compression(withDefaultCompression = "gzip", withDisabledExtensions = "ext1,ext2,ext3")

            compression.algorithmFor(Paths.get("/tmp/file1")) shouldBe ("gzip")
            compression.algorithmFor(Paths.get("/tmp/file1.ext")) shouldBe ("gzip")
            compression.algorithmFor(Paths.get("/tmp/file1.ext1.ext")) shouldBe ("gzip")
            compression.algorithmFor(Paths.get("/tmp/file1.ext1")) shouldBe ("none")
            compression.algorithmFor(Paths.get("/tmp/file1.ext2")) shouldBe ("none")
            compression.algorithmFor(Paths.get("/tmp/file1.ext3")) shouldBe ("none")
            compression.algorithmFor(Paths.get("/tmp/file1ext1")) shouldBe ("gzip")
            compression.algorithmFor(Paths.get("/tmp/file1ext2")) shouldBe ("gzip")
            compression.algorithmFor(Paths.get("/tmp/file1ext3")) shouldBe ("gzip")
        }

        "provide encoders for source entities" {
            val compression = Compression(withDefaultCompression = "deflate", withDisabledExtensions = "a,b,c")

            compression.encoderFor(
                entity = SourceEntity(
                    path = Paths.get("/tmp/a"),
                    existingMetadata = null,
                    currentMetadata = Fixtures.Metadata.FileOneMetadata
                )
            ) shouldBe (Identity)

            compression.encoderFor(
                entity = SourceEntity(
                    path = Paths.get("/tmp/a"),
                    existingMetadata = null,
                    currentMetadata = Fixtures.Metadata.FileTwoMetadata
                )
            ) shouldBe (Gzip)

            compression.encoderFor(
                entity = SourceEntity(
                    path = Paths.get("/tmp/a"),
                    existingMetadata = null,
                    currentMetadata = Fixtures.Metadata.FileThreeMetadata
                )
            ) shouldBe (Deflate)
        }

        "fail to provide encoders for directories" {
            val compression = Compression(withDefaultCompression = "deflate", withDisabledExtensions = "a,b,c")

            shouldThrow<IllegalArgumentException> {
                compression.encoderFor(
                    entity = SourceEntity(
                        path = Paths.get("/tmp/a"),
                        existingMetadata = null,
                        currentMetadata = Fixtures.Metadata.DirectoryOneMetadata
                    )
                )
            }
        }

        "provide decoders for target entities" {
            val compression = Compression(withDefaultCompression = "deflate", withDisabledExtensions = "a,b,c")

            compression.decoderFor(
                entity = TargetEntity(
                    path = Paths.get("/tmp/a"),
                    destination = TargetEntity.Destination.Default,
                    existingMetadata = Fixtures.Metadata.FileOneMetadata,
                    currentMetadata = null
                )
            ) shouldBe (Identity)

            compression.decoderFor(
                entity = TargetEntity(
                    path = Paths.get("/tmp/a"),
                    destination = TargetEntity.Destination.Default,
                    existingMetadata = Fixtures.Metadata.FileTwoMetadata,
                    currentMetadata = null
                )
            ) shouldBe (Gzip)

            compression.decoderFor(
                entity = TargetEntity(
                    path = Paths.get("/tmp/a"),
                    destination = TargetEntity.Destination.Default,
                    existingMetadata = Fixtures.Metadata.FileThreeMetadata,
                    currentMetadata = null
                )
            ) shouldBe (Deflate)
        }

        "fail to provide decoders for directories" {
            val compression = Compression(withDefaultCompression = "deflate", withDisabledExtensions = "a,b,c")

            shouldThrow<IllegalArgumentException> {
                compression.decoderFor(
                    entity = TargetEntity(
                        path = Paths.get("/tmp/a"),
                        destination = TargetEntity.Destination.Default,
                        existingMetadata = Fixtures.Metadata.DirectoryTwoMetadata,
                        currentMetadata = null
                    )
                )
            }
        }
    }
})
