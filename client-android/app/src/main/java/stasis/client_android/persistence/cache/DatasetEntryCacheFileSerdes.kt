package stasis.client_android.persistence.cache

import okio.ByteString.Companion.toByteString
import stasis.client_android.lib.model.server.datasets.DatasetEntry
import stasis.client_android.lib.model.server.datasets.DatasetEntry.Companion.toByteString
import stasis.client_android.lib.model.server.datasets.DatasetEntry.Companion.toDatasetEntry
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.utils.Cache
import stasis.client_android.lib.utils.Try

object DatasetEntryCacheFileSerdes : Cache.File.Serdes<DatasetEntryId, DatasetEntry> {
    override fun serializeKey(key: DatasetEntryId): String = key.toString()

    override fun serializeValue(value: DatasetEntry): ByteArray =
        value.toByteString().toByteArray()

    override fun deserializeValue(value: ByteArray): Try<DatasetEntry> =
        value.toByteString().toDatasetEntry()
}
