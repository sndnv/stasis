package stasis.client_android.persistence.cache

import okio.ByteString.Companion.toByteString
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.DatasetMetadata.Companion.toByteString
import stasis.client_android.lib.model.DatasetMetadata.Companion.toDatasetMetadata
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.utils.Cache
import stasis.client_android.lib.utils.Try

object DatasetMetadataCacheFileSerdes : Cache.File.Serdes<DatasetEntryId, DatasetMetadata> {
    override fun serializeKey(key: DatasetEntryId): String = key.toString()

    override fun serializeValue(value: DatasetMetadata): ByteArray =
        value.toByteString().toByteArray()

    override fun deserializeValue(value: ByteArray): Try<DatasetMetadata> =
        value.toByteString().toDatasetMetadata()
}
