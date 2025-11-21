package stasis.test.client_android.lib.api.clients.caching

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import stasis.client_android.lib.api.clients.caching.DatasetEntriesForDefinition
import stasis.client_android.lib.api.clients.caching.DatasetEntriesForDefinition.Companion.toByteString
import stasis.client_android.lib.api.clients.caching.DatasetEntriesForDefinition.Companion.toDatasetEntriesForDefinition
import stasis.client_android.lib.utils.Try
import java.io.EOFException
import java.util.UUID

class DatasetEntriesForDefinitionSpec : WordSpec({
    "A DatasetEntriesForDefinition" should {
        val entry = DatasetEntriesForDefinition(
            entries = mapOf(
                UUID.fromString("03455212-735b-444a-9886-6762432ebae9") to java.time.Instant.parse("2020-01-02T03:04:05Z"),
                UUID.fromString("87f08176-958e-411d-913e-b29798053030") to java.time.Instant.parse("2020-01-03T03:04:05Z"),
                UUID.fromString("4cc26129-8921-4c1b-a207-f6b0edc1b4ee") to java.time.Instant.parse("2020-01-04T03:04:05Z"),
            ),
            latest = UUID.fromString("03455212-735b-444a-9886-6762432ebae9")
        )

        val serializedEntry =
            "Ci0KJDAzNDU1MjEyLTczNWItNDQ0YS05ODg2LTY3NjI0" +
                    "MzJlYmFlORCImdeh9i0KLQokODdmMDgxNzYtOTU4ZS" +
                    "00MTFkLTkxM2UtYjI5Nzk4MDUzMDMwEIjR8Mr2LQot" +
                    "CiQ0Y2MyNjEyOS04OTIxLTRjMWItYTIwNy1mNmIwZW" +
                    "RjMWI0ZWUQiImK9PYtEiQwMzQ1NTIxMi03MzViLTQ0" +
                    "NGEtOTg4Ni02NzYyNDMyZWJhZTk="


        "be serializable to byte string" {
            entry.toByteString() shouldBe (serializedEntry.decodeBase64())
        }

        "be deserializable from a valid byte string" {
            serializedEntry.decodeBase64()?.toDatasetEntriesForDefinition() shouldBe (Try.Success(entry))
        }

        "fail to be deserialized from an invalid byte string" {
            shouldThrow<EOFException> {
                "invalid".toByteArray().toByteString().toAsciiLowercase().toDatasetEntriesForDefinition().get()
            }
        }
    }
})
