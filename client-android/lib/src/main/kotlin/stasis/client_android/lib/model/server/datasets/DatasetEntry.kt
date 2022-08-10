package stasis.client_android.lib.model.server.datasets

import com.squareup.moshi.JsonClass
import okio.ByteString
import stasis.client_android.lib.model.core.CrateId
import stasis.client_android.lib.model.server.devices.DeviceId
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Companion.map
import java.time.Instant
import java.util.UUID

@JsonClass(generateAdapter = true)
data class DatasetEntry(
    val id: DatasetEntryId,
    val definition: DatasetDefinitionId,
    val device: DeviceId,
    val data: Set<CrateId>,
    val metadata: CrateId,
    val created: Instant
) {
    companion object {
        fun DatasetEntry.toByteString(): ByteString =
            stasis.client_android.lib.model.proto.DatasetEntry(
                id = this.id.toProtobuf(),
                definition = this.definition.toProtobuf(),
                device = this.device.toProtobuf(),
                data_ = this.data.map { it.toProtobuf() }.toList(),
                metadata = this.metadata.toProtobuf(),
                created = this.created.toEpochMilli()
            ).encodeByteString()

        fun ByteString.toDatasetEntry(): Try<DatasetEntry> = Try {
            stasis.client_android.lib.model.proto.DatasetEntry.ADAPTER.decode(this)
        }.map { entry ->
            DatasetEntry(
                id = entry.id.toModel(),
                definition = entry.definition.toModel(),
                device = entry.device.toModel(),
                data = entry.data_.map { it.toModel() }.toSet(),
                metadata = entry.metadata.toModel(),
                created = Instant.ofEpochMilli(entry.created),
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
    }
}

typealias DatasetEntryId = UUID
