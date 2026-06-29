package stasis.test.client_android.lib.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.encodeUtf8
import stasis.client_android.lib.model.EntityMetadata.Companion.toModel
import stasis.client_android.lib.model.EntityMetadata.Companion.toProto
import stasis.client_android.lib.utils.Try.Success
import stasis.test.client_android.lib.Fixtures
import java.util.UUID

class EntityMetadataSpec : WordSpec({
    "EntityMetadata" should {
        "be serializable to protobuf data" {
            Fixtures.Metadata.FileOneMetadata.toProto() shouldBe (Fixtures.Proto.Metadata.FileOneMetadataProto)
            Fixtures.Metadata.DirectoryOneMetadata.toProto() shouldBe (Fixtures.Proto.Metadata.DirectoryOneMetadataProto)
            Fixtures.Metadata.FileTwoMetadata.toProto() shouldBe (Fixtures.Proto.Metadata.FileTwoMetadataProto)
            Fixtures.Metadata.DirectoryTwoMetadata.toProto() shouldBe (Fixtures.Proto.Metadata.DirectoryTwoMetadataProto)
            Fixtures.Metadata.LibraryOneMetadata.toProto() shouldBe (Fixtures.Proto.Metadata.LibraryOneMetadataProto)
        }

        "be deserializable from valid protobuf data" {
            Fixtures.Proto.Metadata.FileOneMetadataProto.toModel() shouldBe (Success(Fixtures.Metadata.FileOneMetadata))
            Fixtures.Proto.Metadata.DirectoryOneMetadataProto.toModel() shouldBe (Success(Fixtures.Metadata.DirectoryOneMetadata))
            Fixtures.Proto.Metadata.FileTwoMetadataProto.toModel() shouldBe (Success(Fixtures.Metadata.FileTwoMetadata))
            Fixtures.Proto.Metadata.DirectoryTwoMetadataProto.toModel() shouldBe (Success(Fixtures.Metadata.DirectoryTwoMetadata))
            Fixtures.Proto.Metadata.LibraryOneMetadataProto.toModel() shouldBe (Success(Fixtures.Metadata.LibraryOneMetadata))
        }

        "fail to be deserialized when empty entity is provided" {
            val e = shouldThrow<IllegalArgumentException> { Fixtures.Proto.Metadata.EmptyMetadataProto.toModel().get() }
            e.message shouldBe ("Expected entity in metadata but none was found")
        }

        "support comparing metadata for changes, ignoring file compression" {
            Fixtures.Metadata.FileOneMetadata
                .hasChanged(comparedTo = Fixtures.Metadata.FileOneMetadata) shouldBe (false)

            Fixtures.Metadata.FileOneMetadata
                .hasChanged(comparedTo = Fixtures.Metadata.FileTwoMetadata) shouldBe (true)

            Fixtures.Metadata.FileOneMetadata
                .hasChanged(comparedTo = Fixtures.Metadata.FileThreeMetadata) shouldBe (true)

            Fixtures.Metadata.FileOneMetadata
                .hasChanged(comparedTo = Fixtures.Metadata.FileOneMetadata.copy(compression = "other")) shouldBe (false)

            Fixtures.Metadata.FileOneMetadata
                .hasChanged(comparedTo = Fixtures.Metadata.DirectoryOneMetadata) shouldBe (true)

            Fixtures.Metadata.DirectoryOneMetadata
                .hasChanged(comparedTo = Fixtures.Metadata.DirectoryOneMetadata) shouldBe (false)

            Fixtures.Metadata.DirectoryOneMetadata
                .hasChanged(comparedTo = Fixtures.Metadata.DirectoryTwoMetadata) shouldBe (true)
        }

        "support comparing library metadata for changes, ignoring compression but not attributes" {
            Fixtures.Metadata.LibraryOneMetadata
                .hasChanged(comparedTo = Fixtures.Metadata.LibraryOneMetadata) shouldBe (false)

            Fixtures.Metadata.LibraryOneMetadata
                .hasChanged(comparedTo = Fixtures.Metadata.LibraryOneMetadata.copy(compression = "other")) shouldBe (false)

            Fixtures.Metadata.LibraryOneMetadata
                .hasChanged(
                    comparedTo = Fixtures.Metadata.LibraryOneMetadata.copy(attributes = "favorite=false".encodeUtf8())
                ) shouldBe (true)

            Fixtures.Metadata.LibraryOneMetadata
                .hasChanged(comparedTo = Fixtures.Metadata.FileOneMetadata) shouldBe (true)
        }

        "provide content-bearing metadata with updated crates and compression" {
            val updatedCrates = mapOf("photos:/album/img.heic_0" to UUID.randomUUID())

            Fixtures.Metadata.LibraryOneMetadata.withCrates(updatedCrates).crates shouldBe (updatedCrates)
            Fixtures.Metadata.LibraryOneMetadata.withCompression("gzip").compression shouldBe ("gzip")

            Fixtures.Metadata.FileOneMetadata.withCrates(updatedCrates).crates shouldBe (updatedCrates)
            Fixtures.Metadata.FileOneMetadata.withCompression("gzip").compression shouldBe ("gzip")
        }

        "support coercion to filesystem metadata" {
            Fixtures.Metadata.FileOneMetadata.asFilesystem() shouldBe (Fixtures.Metadata.FileOneMetadata)
            Fixtures.Metadata.DirectoryOneMetadata.asFilesystem() shouldBe (Fixtures.Metadata.DirectoryOneMetadata)

            val e = shouldThrow<IllegalArgumentException> { Fixtures.Metadata.LibraryOneMetadata.asFilesystem() }
            e.message shouldBe ("Requested filesystem metadata but library metadata for [photos:/album/img.heic] found")
        }
    }
})
