package stasis.test.client_android.lib.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.model.FilesystemMetadata
import stasis.client_android.lib.model.FilesystemMetadata.Companion.toModel
import stasis.client_android.lib.model.FilesystemMetadata.Companion.toProto
import stasis.client_android.lib.model.FilesystemMetadata.EntityState.Companion.toModel
import stasis.client_android.lib.model.FilesystemMetadata.EntityState.Companion.toProto
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.utils.Try.Success
import stasis.test.client_android.lib.Fixtures
import java.nio.file.Paths
import java.util.UUID

class FilesystemMetadataSpec : WordSpec({
    val entry = UUID.randomUUID()

    fun protoEntityStateNew(): stasis.client_android.lib.model.proto.EntityState =
        stasis.client_android.lib.model.proto.EntityState(
            present_new = stasis.client_android.lib.model.proto.EntityState.PresentNew()
        )

    fun protoEntityStateExisting(entry: DatasetEntryId?): stasis.client_android.lib.model.proto.EntityState =
        stasis.client_android.lib.model.proto.EntityState(
            present_existing = stasis.client_android.lib.model.proto.EntityState.PresentExisting(
                entry = entry?.let {
                    stasis.client_android.lib.model.proto.Uuid(
                        mostSignificantBits = it.mostSignificantBits,
                        leastSignificantBits = it.leastSignificantBits
                    )
                }
            )
        )

    fun protoEntityStateUpdated(): stasis.client_android.lib.model.proto.EntityState =
        stasis.client_android.lib.model.proto.EntityState(
            present_updated = stasis.client_android.lib.model.proto.EntityState.PresentUpdated()
        )

    fun protoEntityStateEmpty(): stasis.client_android.lib.model.proto.EntityState =
        stasis.client_android.lib.model.proto.EntityState()

    "FilesystemMetadata" should {

        val filesystemMetadata = FilesystemMetadata(
            entities = mapOf(
                Fixtures.Metadata.FileOneMetadata.path to FilesystemMetadata.EntityState.New,
                Fixtures.Metadata.FileTwoMetadata.path to FilesystemMetadata.EntityState.Updated,
                Fixtures.Metadata.FileThreeMetadata.path to FilesystemMetadata.EntityState.Existing(entry)
            )
        )

        val filesystemMetadataProto = stasis.client_android.lib.model.proto.FilesystemMetadata(
            entities = mapOf(
                Fixtures.Metadata.FileOneMetadata.path.toAbsolutePath().toString() to protoEntityStateNew(),
                Fixtures.Metadata.FileTwoMetadata.path.toAbsolutePath().toString() to protoEntityStateUpdated(),
                Fixtures.Metadata.FileThreeMetadata.path.toAbsolutePath().toString() to protoEntityStateExisting(entry)
            )
        )

        "be serializable to protobuf data" {
            filesystemMetadata.toProto() shouldBe (filesystemMetadataProto)
        }

        "be deserializable from valid protobuf data" {
            filesystemMetadataProto.toModel() shouldBe (Success(filesystemMetadata))
        }

        "fail to be deserialized if no metadata is provided" {
            val e = shouldThrow<IllegalArgumentException> {
                (null as stasis.client_android.lib.model.proto.FilesystemMetadata?).toModel().get()
            }
            e.message shouldBe ("No filesystem metadata provided")
        }

        "allow to be created with new files" {
            val created = FilesystemMetadata(
                changes = listOf(
                    Fixtures.Metadata.FileOneMetadata.path,
                    Fixtures.Metadata.FileTwoMetadata.path
                )
            )

            created shouldBe (
                    FilesystemMetadata(
                        entities = mapOf(
                            Fixtures.Metadata.FileOneMetadata.path to FilesystemMetadata.EntityState.New,
                            Fixtures.Metadata.FileTwoMetadata.path to FilesystemMetadata.EntityState.New
                        )
                    )
                    )
        }

        "allow to be updated with new files" {
            val newEntry = UUID.randomUUID()

            val newFile = Paths.get("/tmp/file/five")

            val updated = filesystemMetadata.updated(
                changes = listOf(
                    Fixtures.Metadata.FileOneMetadata.path, // updated
                    newFile // new
                ),
                latestEntry = newEntry
            )

            updated shouldBe (
                    FilesystemMetadata(
                        entities = mapOf(
                            Fixtures.Metadata.FileOneMetadata.path to FilesystemMetadata.EntityState.Updated,
                            Fixtures.Metadata.FileTwoMetadata.path to FilesystemMetadata.EntityState.Existing(newEntry),
                            Fixtures.Metadata.FileThreeMetadata.path to FilesystemMetadata.EntityState.Existing(entry),
                            newFile to FilesystemMetadata.EntityState.New
                        )
                    )
                    )

            val latestEntry = UUID.randomUUID()

            updated.updated(changes = emptyList(), latestEntry = latestEntry) shouldBe (
                    FilesystemMetadata(
                        entities = mapOf(
                            Fixtures.Metadata.FileOneMetadata.path to FilesystemMetadata.EntityState.Existing(latestEntry),
                            Fixtures.Metadata.FileTwoMetadata.path to FilesystemMetadata.EntityState.Existing(newEntry),
                            Fixtures.Metadata.FileThreeMetadata.path to FilesystemMetadata.EntityState.Existing(entry),
                            newFile to FilesystemMetadata.EntityState.Existing(latestEntry)
                        )
                    )
                    )
        }
    }

    "Filesystem metadata EntityState" should {
        "be serializable to protobuf data" {
            FilesystemMetadata.EntityState.New.toProto() shouldBe (protoEntityStateNew())

            FilesystemMetadata.EntityState.Existing(entry).toProto() shouldBe (protoEntityStateExisting(entry))

            FilesystemMetadata.EntityState.Updated.toProto() shouldBe (protoEntityStateUpdated())
        }

        "be deserializable from valid protobuf data" {
            protoEntityStateNew().toModel() shouldBe (Success(FilesystemMetadata.EntityState.New))

            protoEntityStateExisting(entry).toModel() shouldBe (Success(FilesystemMetadata.EntityState.Existing(entry)))

            protoEntityStateUpdated().toModel() shouldBe (Success(FilesystemMetadata.EntityState.Updated))
        }

        "fail if no entry is provided for an existing file state" {
            val e = shouldThrow<IllegalArgumentException> { protoEntityStateExisting(entry = null).toModel().get() }
            e.message shouldBe ("No entry ID found for existing file")
        }

        "fail if an empty file state is provided" {
            val e = shouldThrow<IllegalArgumentException> { protoEntityStateEmpty().toModel().get() }
            e.message shouldBe ("Unexpected empty file state encountered")
        }
    }
})
