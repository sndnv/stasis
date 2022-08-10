package stasis.test.client_android.lib.model.server.datasets

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import stasis.client_android.lib.model.server.datasets.DatasetEntry
import stasis.client_android.lib.model.server.datasets.DatasetEntry.Companion.toByteString
import stasis.client_android.lib.model.server.datasets.DatasetEntry.Companion.toDatasetEntry
import stasis.client_android.lib.model.server.datasets.DatasetEntry.Companion.toModel
import stasis.client_android.lib.model.server.datasets.DatasetEntry.Companion.toProtobuf
import stasis.client_android.lib.utils.Try
import java.io.EOFException
import java.time.Instant
import java.util.UUID

class DatasetEntrySpec : WordSpec({
    "A DatasetEntry" should {
        val datasetEntry = DatasetEntry(
            id = UUID.fromString("71a357e7-856f-4ae1-9d4d-abf424daddcd"),
            definition = UUID.fromString("03455212-735b-444a-9886-6762432ebae9"),
            device = UUID.fromString("0fabfb8e-aa45-4e9e-bca1-cca20b09097d"),
            data = setOf(
                UUID.fromString("87f08176-958e-411d-913e-b29798053030"),
                UUID.fromString("0b2ca24f-f029-4733-8a73-8f835245d110"),
                UUID.fromString("4cc26129-8921-4c1b-a207-f6b0edc1b4ee")
            ),
            metadata = UUID.fromString("56179ef2-7897-488f-9cf0-6aa08c57b259"),
            created = Instant.parse("2020-01-02T03:04:05Z")
        )

        val serializedDatasetEntry =
            "ChUI4ZW9q/j81dFxEM2766bC/uqmnQESFQjKiO2ap8LU" +
                    "ogMQ6fW6maTsmcOYARoVCJ6dldLq8f7VDxD9kqTYoJ" +
                    "Tz0LwBIhYInYK5rOmuoPiHARCw4JTA+dKsn5EBIhUI" +
                    "s46lgf/JqJYLEJCil5K18OO5igEiFQibmIXJmKWY4U" +
                    "wQ7umG7o7W/YOiASoVCI+R3cSn3ueLVhDZ5N7iiNSa" +
                    "+JwBMIiZ16H2LQ=="

        "be serializable to byte string" {
            datasetEntry.toByteString() shouldBe (serializedDatasetEntry.decodeBase64())
        }

        "be deserializable from a valid byte string" {
            serializedDatasetEntry.decodeBase64()?.toDatasetEntry() shouldBe (Try.Success(datasetEntry))
        }

        "fail to be deserialized from an invalid byte string" {
            shouldThrow<EOFException> {
                "invalid".toByteArray().toByteString().toAsciiLowercase().toDatasetEntry().get()
            }
        }

        "serialize UUIDs" {
            val javaUuid = UUID.fromString("56179ef2-7897-488f-9cf0-6aa08c57b259")

            val protobufUuid = stasis.client_android.lib.model.proto.Uuid(
                mostSignificantBits = javaUuid.mostSignificantBits,
                leastSignificantBits = javaUuid.leastSignificantBits
            )

            javaUuid.toProtobuf() shouldBe (protobufUuid)
        }

        "fail to be deserialized if UUIDs are missing" {
            val emptyProtobufUuid: stasis.client_android.lib.model.proto.Uuid? = null

            shouldThrow<IllegalArgumentException> {
                emptyProtobufUuid.toModel()
            }
        }
    }
})
