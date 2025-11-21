package stasis.client_android.persistence.cache

import okio.ByteString.Companion.toByteString
import stasis.client_android.lib.api.clients.caching.DatasetEntriesForDefinition
import stasis.client_android.lib.api.clients.caching.DatasetEntriesForDefinition.Companion.toByteString
import stasis.client_android.lib.api.clients.caching.DatasetEntriesForDefinition.Companion.toDatasetEntriesForDefinition
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.utils.Cache
import stasis.client_android.lib.utils.Try
import java.util.UUID

object DatasetEntriesForDefinitionCacheFileSerdes :
    Cache.File.Serdes<DatasetDefinitionId, DatasetEntriesForDefinition> {
    override fun serializeKey(key: DatasetDefinitionId): String = key.toString()

    override fun deserializeKey(key: String): DatasetDefinitionId? =
        Try { UUID.fromString(key) }.toOption()

    override fun serializeValue(value: DatasetEntriesForDefinition): ByteArray =
        value.toByteString().toByteArray()

    override fun deserializeValue(value: ByteArray): Try<DatasetEntriesForDefinition> =
        value.toByteString().toDatasetEntriesForDefinition()
}
