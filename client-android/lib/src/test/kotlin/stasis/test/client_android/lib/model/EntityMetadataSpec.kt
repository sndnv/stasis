package stasis.test.client_android.lib.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.model.EntityMetadata.Companion.toModel
import stasis.client_android.lib.model.EntityMetadata.Companion.toProto
import stasis.client_android.lib.utils.Try.Success
import stasis.test.client_android.lib.Fixtures

class EntityMetadataSpec : WordSpec({
    "EntityMetadata" should {
        "be serializable to protobuf data" {
            Fixtures.Metadata.FileOneMetadata.toProto() shouldBe (Fixtures.Proto.Metadata.FileOneMetadataProto)
            Fixtures.Metadata.DirectoryOneMetadata.toProto() shouldBe (Fixtures.Proto.Metadata.DirectoryOneMetadataProto)
            Fixtures.Metadata.FileTwoMetadata.toProto() shouldBe (Fixtures.Proto.Metadata.FileTwoMetadataProto)
            Fixtures.Metadata.DirectoryTwoMetadata.toProto() shouldBe (Fixtures.Proto.Metadata.DirectoryTwoMetadataProto)
        }

        "be deserializable from valid protobuf data" {
            Fixtures.Proto.Metadata.FileOneMetadataProto.toModel() shouldBe (Success(Fixtures.Metadata.FileOneMetadata))
            Fixtures.Proto.Metadata.DirectoryOneMetadataProto.toModel() shouldBe (Success(Fixtures.Metadata.DirectoryOneMetadata))
            Fixtures.Proto.Metadata.FileTwoMetadataProto.toModel() shouldBe (Success(Fixtures.Metadata.FileTwoMetadata))
            Fixtures.Proto.Metadata.DirectoryTwoMetadataProto.toModel() shouldBe (Success(Fixtures.Metadata.DirectoryTwoMetadata))
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
    }
})
