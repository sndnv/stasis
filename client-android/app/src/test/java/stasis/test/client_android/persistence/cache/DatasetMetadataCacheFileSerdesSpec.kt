package stasis.test.client_android.persistence.cache

import okio.ByteString.Companion.decodeBase64
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.FilesystemMetadata
import stasis.client_android.lib.utils.Try
import stasis.client_android.persistence.cache.DatasetMetadataCacheFileSerdes
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class DatasetMetadataCacheFileSerdesSpec {
    private val datasetMetadata = DatasetMetadata(
        contentChanged = emptyMap(),
        metadataChanged = emptyMap(),
        filesystem = FilesystemMetadata(
            entities = emptyMap()
        )
    )

    private val serializedDatasetMetadata = "GgA="

    @Test
    fun serializeKeys() {
        val key = "256daa1a-59ec-408c-8b0b-42909e01250e"
        assertThat(
            DatasetMetadataCacheFileSerdes.serializeKey(UUID.fromString(key)),
            equalTo(key)
        )
    }

    @Test
    fun serializeValues() {
        assertThat(
            DatasetMetadataCacheFileSerdes.serializeValue(datasetMetadata),
            equalTo(serializedDatasetMetadata.decodeBase64()?.toByteArray())
        )
    }

    @Test
    fun deserializeValues() {
        assertThat(
            DatasetMetadataCacheFileSerdes.deserializeValue(serializedDatasetMetadata.decodeBase64()!!.toByteArray()),
            equalTo(Try.Success(datasetMetadata))
        )
    }
}
