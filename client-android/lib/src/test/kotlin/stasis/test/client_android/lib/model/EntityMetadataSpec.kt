package stasis.test.client_android.lib.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.toByteString
import stasis.client_android.lib.model.EntityMetadata.Companion.toModel
import stasis.client_android.lib.model.EntityMetadata.Companion.toProto
import stasis.client_android.lib.utils.Try.Success
import stasis.test.client_android.lib.Fixtures

class EntityMetadataSpec : WordSpec({
    "EntityMetadata" should {
        val actualFileOneMetadata = stasis.client_android.lib.model.proto.FileMetadata(
            path = Fixtures.Metadata.FileOneMetadata.path.toAbsolutePath().toString(),
            size = Fixtures.Metadata.FileOneMetadata.size,
            link = Fixtures.Metadata.FileOneMetadata.link?.toAbsolutePath()?.toString() ?: "",
            isHidden = Fixtures.Metadata.FileOneMetadata.isHidden,
            created = Fixtures.Metadata.FileOneMetadata.created.epochSecond,
            updated = Fixtures.Metadata.FileOneMetadata.updated.epochSecond,
            owner = Fixtures.Metadata.FileOneMetadata.owner,
            group = Fixtures.Metadata.FileOneMetadata.group,
            permissions = Fixtures.Metadata.FileOneMetadata.permissions,
            checksum = Fixtures.Metadata.FileOneMetadata.checksum.toByteArray().toByteString(),
            crates = Fixtures.Metadata.FileOneMetadata.crates.map { (path, uuid) ->
                path.toString() to stasis.client_android.lib.model.proto.Uuid(
                    mostSignificantBits = uuid.mostSignificantBits,
                    leastSignificantBits = uuid.leastSignificantBits
                )
            }.toMap(),
            compression = "none"
        )

        val actualFileTwoMetadata = stasis.client_android.lib.model.proto.FileMetadata(
            path = Fixtures.Metadata.FileTwoMetadata.path.toAbsolutePath().toString(),
            size = Fixtures.Metadata.FileTwoMetadata.size,
            link = Fixtures.Metadata.FileTwoMetadata.link?.toAbsolutePath()?.toString() ?: "",
            isHidden = Fixtures.Metadata.FileTwoMetadata.isHidden,
            created = Fixtures.Metadata.FileTwoMetadata.created.epochSecond,
            updated = Fixtures.Metadata.FileTwoMetadata.updated.epochSecond,
            owner = Fixtures.Metadata.FileTwoMetadata.owner,
            group = Fixtures.Metadata.FileTwoMetadata.group,
            permissions = Fixtures.Metadata.FileTwoMetadata.permissions,
            checksum = Fixtures.Metadata.FileTwoMetadata.checksum.toByteArray().toByteString(),
            crates = Fixtures.Metadata.FileTwoMetadata.crates.map { (path, uuid) ->
                path.toString() to stasis.client_android.lib.model.proto.Uuid(
                    mostSignificantBits = uuid.mostSignificantBits,
                    leastSignificantBits = uuid.leastSignificantBits
                )
            }.toMap(),
            compression = "gzip"
        )

        val fileOneMetadataProto = stasis.client_android.lib.model.proto.EntityMetadata(file_ = actualFileOneMetadata)

        val fileTwoMetadataProto = stasis.client_android.lib.model.proto.EntityMetadata(file_ = actualFileTwoMetadata)

        val actualDirectoryOneMetadata = stasis.client_android.lib.model.proto.DirectoryMetadata(
            path = Fixtures.Metadata.DirectoryOneMetadata.path.toAbsolutePath().toString(),
            link = Fixtures.Metadata.DirectoryOneMetadata.link?.toAbsolutePath()?.toString() ?: "",
            isHidden = Fixtures.Metadata.DirectoryOneMetadata.isHidden,
            created = Fixtures.Metadata.DirectoryOneMetadata.created.epochSecond,
            updated = Fixtures.Metadata.DirectoryOneMetadata.updated.epochSecond,
            owner = Fixtures.Metadata.DirectoryOneMetadata.owner,
            group = Fixtures.Metadata.DirectoryOneMetadata.group,
            permissions = Fixtures.Metadata.DirectoryOneMetadata.permissions
        )

        val actualDirectoryTwoMetadata = stasis.client_android.lib.model.proto.DirectoryMetadata(
            path = Fixtures.Metadata.DirectoryTwoMetadata.path.toAbsolutePath().toString(),
            link = Fixtures.Metadata.DirectoryTwoMetadata.link?.toAbsolutePath()?.toString() ?: "",
            isHidden = Fixtures.Metadata.DirectoryTwoMetadata.isHidden,
            created = Fixtures.Metadata.DirectoryTwoMetadata.created.epochSecond,
            updated = Fixtures.Metadata.DirectoryTwoMetadata.updated.epochSecond,
            owner = Fixtures.Metadata.DirectoryTwoMetadata.owner,
            group = Fixtures.Metadata.DirectoryTwoMetadata.group,
            permissions = Fixtures.Metadata.DirectoryTwoMetadata.permissions
        )

        val directoryOneMetadataProto =
            stasis.client_android.lib.model.proto.EntityMetadata(directory = actualDirectoryOneMetadata)

        val directoryTwoMetadataProto =
            stasis.client_android.lib.model.proto.EntityMetadata(directory = actualDirectoryTwoMetadata)

        val emptyMetadataProto = stasis.client_android.lib.model.proto.EntityMetadata()

        "be serializable to protobuf data" {
            Fixtures.Metadata.FileOneMetadata.toProto() shouldBe (fileOneMetadataProto)
            Fixtures.Metadata.DirectoryOneMetadata.toProto() shouldBe (directoryOneMetadataProto)
            Fixtures.Metadata.FileTwoMetadata.toProto() shouldBe (fileTwoMetadataProto)
            Fixtures.Metadata.DirectoryTwoMetadata.toProto() shouldBe (directoryTwoMetadataProto)
        }

        "be deserializable from valid protobuf data" {
            fileOneMetadataProto.toModel() shouldBe (Success(Fixtures.Metadata.FileOneMetadata))
            directoryOneMetadataProto.toModel() shouldBe (Success(Fixtures.Metadata.DirectoryOneMetadata))
            fileTwoMetadataProto.toModel() shouldBe (Success(Fixtures.Metadata.FileTwoMetadata))
            directoryTwoMetadataProto.toModel() shouldBe (Success(Fixtures.Metadata.DirectoryTwoMetadata))
        }

        "fail to be deserialized when empty entity is provided" {
            val e = shouldThrow<IllegalArgumentException> { emptyMetadataProto.toModel().get() }
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
    }
})
