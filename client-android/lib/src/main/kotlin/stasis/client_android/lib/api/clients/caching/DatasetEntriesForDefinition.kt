package stasis.client_android.lib.api.clients.caching

import okio.ByteString
import stasis.client_android.lib.model.server.datasets.DatasetEntry
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Companion.map
import java.time.Instant
import java.util.UUID

data class DatasetEntriesForDefinition(
    val entries: Map<DatasetEntryId, Instant>,
    val latest: DatasetEntryId?
) {
    fun withEntry(entry: DatasetEntry): DatasetEntriesForDefinition {
        val updated = entries + (entry.id to entry.created)
        return copy(entries = updated, latest = updated.maxByOrNull { it.value }?.key)
    }

    companion object {
        operator fun invoke(entries: List<DatasetEntry>): DatasetEntriesForDefinition =
            DatasetEntriesForDefinition(
                entries = entries.associate { it.id to it.created },
                latest = entries.maxByOrNull { it.created }?.id
            )

        fun empty(): DatasetEntriesForDefinition =
            DatasetEntriesForDefinition(
                entries = emptyMap(),
                latest = null
            )

        fun DatasetEntriesForDefinition.toByteString(): ByteString =
            stasis.client_android.lib.model.proto.DatasetEntriesForDefinition(
                entries = this.entries.map { it.key.toString() to it.value.toEpochMilli() }.toMap(),
                latest = this.latest?.toString(),
            ).encodeByteString()

        fun ByteString.toDatasetEntriesForDefinition(): Try<DatasetEntriesForDefinition> = Try {
            stasis.client_android.lib.model.proto.DatasetEntriesForDefinition.ADAPTER.decode(this)
        }.map { entry ->
            DatasetEntriesForDefinition(
                entries = entry.entries.map { UUID.fromString(it.key) to Instant.ofEpochMilli(it.value) }.toMap(),
                latest = entry.latest?.let { UUID.fromString(it) }
            )
        }
    }
}
