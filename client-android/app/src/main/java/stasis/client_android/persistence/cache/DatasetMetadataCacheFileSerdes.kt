package stasis.client_android.persistence.cache

import okio.Buffer
import okio.ByteString.Companion.toByteString
import okio.buffer
import stasis.client_android.lib.compression.Gzip
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.DatasetMetadata.Companion.toByteString
import stasis.client_android.lib.model.DatasetMetadata.Companion.toDatasetMetadata
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.utils.Cache
import stasis.client_android.lib.utils.Try

object DatasetMetadataCacheFileSerdes : Cache.File.Serdes<DatasetEntryId, DatasetMetadata> {
    private val compressor = Gzip

    override fun serializeKey(key: DatasetEntryId): String = key.toString()

    override fun serializeValue(value: DatasetMetadata): ByteArray =
        compressor
            .compress(Buffer().write(value.toByteString()))
            .buffer()
            .use { it.readByteString() }
            .toByteArray()

    override fun deserializeValue(value: ByteArray): Try<DatasetMetadata> =
        compressor.decompress(Buffer().write(value.toByteString()))
            .buffer()
            .use { it.readByteString().toDatasetMetadata() }
}
