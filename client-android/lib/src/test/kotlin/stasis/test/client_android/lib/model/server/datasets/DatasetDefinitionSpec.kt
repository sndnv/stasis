package stasis.test.client_android.lib.model.server.datasets

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetDefinition.Companion.toByteString
import stasis.client_android.lib.model.server.datasets.DatasetDefinition.Companion.toDatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetDefinition.Companion.toModel
import stasis.client_android.lib.model.server.datasets.DatasetDefinition.Companion.toProtobuf
import stasis.client_android.lib.utils.Try
import java.io.EOFException
import java.time.Duration
import java.time.Instant
import java.util.UUID

class DatasetDefinitionSpec : WordSpec({
    "A DatasetDefinition" should {
        val definition = DatasetDefinition(
            id = UUID.fromString("03455212-735b-444a-9886-6762432ebae9"),
            info = "test-info",
            device = UUID.fromString("0fabfb8e-aa45-4e9e-bca1-cca20b09097d"),
            redundantCopies = 2,
            existingVersions = DatasetDefinition.Retention(
                policy = DatasetDefinition.Retention.Policy.AtMost(versions = 42),
                duration = Duration.ofSeconds(21)
            ),
            removedVersions = DatasetDefinition.Retention(
                policy = DatasetDefinition.Retention.Policy.All,
                duration = Duration.ofSeconds(0)
            ),
            created = Instant.parse("2020-01-02T03:04:05Z"),
            updated = Instant.parse("2020-01-02T13:04:05Z")
        )

        val serializedDefinition =
            "ChUIyojtmqfC1KIDEOn1upmk7JnDmAESCXRlc3QtaW5m" +
                    "bxoVCJ6dldLq8f7VDxD9kqTYoJTz0LwBIAIqCgoECg" +
                    "IIKhCIpAEyBAoCGgA4iJnXofYtQIi77LL2LQ=="

        "be serializable to byte string" {
            definition.toByteString() shouldBe (serializedDefinition.decodeBase64())
        }

        "be deserializable from a valid byte string" {
            serializedDefinition.decodeBase64()?.toDatasetDefinition() shouldBe (Try.Success(definition))
        }

        "fail to be deserialized from an invalid byte string" {
            shouldThrow<EOFException> {
                "invalid".toByteArray().toByteString().toAsciiLowercase().toDatasetDefinition().get()
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

        "serialize and deserialize retention policies" {
            val atMost = DatasetDefinition.Retention(
                policy = DatasetDefinition.Retention.Policy.AtMost(versions = 1),
                duration = Duration.ofSeconds(2)
            )
            val atMostProtobuf = stasis.client_android.lib.model.proto.DatasetDefinition.Retention(
                policy = stasis.client_android.lib.model.proto.DatasetDefinition.Retention.Policy(
                    atMost = stasis.client_android.lib.model.proto.DatasetDefinition.Retention.Policy.AtMost(versions = 1)
                ),
                duration = 2000
            )

            val latestOnly = DatasetDefinition.Retention(
                policy = DatasetDefinition.Retention.Policy.LatestOnly,
                duration = Duration.ofSeconds(3)
            )
            val latestOnlyProtobuf = stasis.client_android.lib.model.proto.DatasetDefinition.Retention(
                policy = stasis.client_android.lib.model.proto.DatasetDefinition.Retention.Policy(
                    latestOnly = stasis.client_android.lib.model.proto.DatasetDefinition.Retention.Policy.LatestOnly()
                ),
                duration = 3000
            )

            val all = DatasetDefinition.Retention(
                policy = DatasetDefinition.Retention.Policy.All,
                duration = Duration.ofSeconds(4)
            )
            val allProtobuf = stasis.client_android.lib.model.proto.DatasetDefinition.Retention(
                policy = stasis.client_android.lib.model.proto.DatasetDefinition.Retention.Policy(
                    all = stasis.client_android.lib.model.proto.DatasetDefinition.Retention.Policy.All()
                ),
                duration = 4000
            )

            atMost.toProtobuf() shouldBe (atMostProtobuf)
            atMostProtobuf.toModel() shouldBe (atMost)

            latestOnly.toProtobuf() shouldBe (latestOnlyProtobuf)
            latestOnlyProtobuf.toModel() shouldBe (latestOnly)

            all.toProtobuf() shouldBe (allProtobuf)
            allProtobuf.toModel() shouldBe (all)
        }

        "fail to be deserialized if retention policy is missing" {
            val empty: stasis.client_android.lib.model.proto.DatasetDefinition.Retention? = null

            shouldThrow<IllegalArgumentException> {
                empty.toModel()
            }

            shouldThrow<IllegalArgumentException> {
                stasis.client_android.lib.model.proto.DatasetDefinition.Retention().toModel()
            }

            shouldThrow<IllegalArgumentException> {
                stasis.client_android.lib.model.proto.DatasetDefinition.Retention(
                    policy = stasis.client_android.lib.model.proto.DatasetDefinition.Retention.Policy()
                ).toModel()
            }
        }
    }
})
