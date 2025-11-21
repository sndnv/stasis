package stasis.client_android.lib.model.server.datasets

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okio.ByteString
import stasis.client_android.lib.model.server.devices.DeviceId
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Companion.map
import java.time.Duration
import java.time.Instant
import java.util.UUID

@JsonClass(generateAdapter = true)
data class DatasetDefinition(
    val id: DatasetDefinitionId,
    val info: String,
    val device: DeviceId,
    @field:Json(name = "redundant_copies")
    val redundantCopies: Int,
    @field:Json(name = "existing_versions")
    val existingVersions: Retention,
    @field:Json(name = "removed_versions")
    val removedVersions: Retention,
    val created: Instant,
    val updated: Instant
) {
    @JsonClass(generateAdapter = true)
    data class Retention(
        val policy: Policy,
        val duration: Duration
    ) {
        sealed class Policy {
            data class AtMost(val versions: Int) : Policy()
            object LatestOnly : Policy()
            object All : Policy()
        }
    }

    companion object {
        fun DatasetDefinition.toByteString(): ByteString =
            stasis.client_android.lib.model.proto.DatasetDefinition(
                id = this.id.toProtobuf(),
                info = this.info,
                device = this.device.toProtobuf(),
                redundant_copies = this.redundantCopies,
                existing_versions = this.existingVersions.toProtobuf(),
                removed_versions = this.removedVersions.toProtobuf(),
                created = this.created.toEpochMilli(),
                updated = this.updated.toEpochMilli(),
            ).encodeByteString()

        fun ByteString.toDatasetDefinition(): Try<DatasetDefinition> = Try {
            stasis.client_android.lib.model.proto.DatasetDefinition.ADAPTER.decode(this)
        }.map { definition ->
            DatasetDefinition(
                id = definition.id.toModel(),
                info = definition.info,
                device = definition.device.toModel(),
                redundantCopies = definition.redundant_copies,
                existingVersions = definition.existing_versions.toModel(),
                removedVersions = definition.removed_versions.toModel(),
                created = Instant.ofEpochMilli(definition.created),
                updated = Instant.ofEpochMilli(definition.updated),
            )
        }

        fun UUID.toProtobuf(): stasis.client_android.lib.model.proto.Uuid =
            stasis.client_android.lib.model.proto.Uuid(
                mostSignificantBits = this.mostSignificantBits,
                leastSignificantBits = this.leastSignificantBits
            )

        fun stasis.client_android.lib.model.proto.Uuid?.toModel(): UUID {
            require(this != null) { "Expected UUID but none was found" }
            return UUID(this.mostSignificantBits, this.leastSignificantBits)
        }

        fun Retention.toProtobuf(): stasis.client_android.lib.model.proto.DatasetDefinition.Retention =
            stasis.client_android.lib.model.proto.DatasetDefinition.Retention(
                policy = when (this.policy) {
                    is Retention.Policy.AtMost -> stasis.client_android.lib.model.proto.DatasetDefinition.Retention.Policy(
                        atMost = stasis.client_android.lib.model.proto.DatasetDefinition.Retention.Policy.AtMost(
                            versions = this.policy.versions
                        )
                    )

                    is Retention.Policy.LatestOnly -> stasis.client_android.lib.model.proto.DatasetDefinition.Retention.Policy(
                        latestOnly = stasis.client_android.lib.model.proto.DatasetDefinition.Retention.Policy.LatestOnly()
                    )

                    is Retention.Policy.All -> stasis.client_android.lib.model.proto.DatasetDefinition.Retention.Policy(
                        all = stasis.client_android.lib.model.proto.DatasetDefinition.Retention.Policy.All()
                    )
                },
                duration = this.duration.toMillis()
            )

        fun stasis.client_android.lib.model.proto.DatasetDefinition.Retention?.toModel(): Retention {
            require(this != null) { "Expected Retention but none was found" }
            require(this.policy != null) { "Expected Retention Policy but none was found" }
            return Retention(
                policy = if (this.policy.atMost != null) {
                    Retention.Policy.AtMost(versions = this.policy.atMost.versions)
                } else if (this.policy.latestOnly != null) {
                    Retention.Policy.LatestOnly
                } else if (this.policy.all != null) {
                    Retention.Policy.All
                } else {
                    throw IllegalArgumentException("Expected Retention Policy with a vallue but none was found")
                },
                duration = Duration.ofMillis(this.duration)
            )
        }
    }
}

typealias DatasetDefinitionId = UUID
