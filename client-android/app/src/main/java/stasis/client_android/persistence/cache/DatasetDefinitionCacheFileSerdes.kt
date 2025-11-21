package stasis.client_android.persistence.cache

import okio.ByteString.Companion.toByteString
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetDefinition.Companion.toByteString
import stasis.client_android.lib.model.server.datasets.DatasetDefinition.Companion.toDatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.utils.Cache
import stasis.client_android.lib.utils.Try
import java.util.UUID

object DatasetDefinitionCacheFileSerdes : Cache.File.Serdes<DatasetDefinitionId, DatasetDefinition> {
    override fun serializeKey(key: DatasetDefinitionId): String = key.toString()

    override fun deserializeKey(key: String): DatasetDefinitionId? =
        Try { UUID.fromString(key) }.toOption()

    override fun serializeValue(value: DatasetDefinition): ByteArray =
        value.toByteString().toByteArray()

    override fun deserializeValue(value: ByteArray): Try<DatasetDefinition> =
        value.toByteString().toDatasetDefinition()
}
