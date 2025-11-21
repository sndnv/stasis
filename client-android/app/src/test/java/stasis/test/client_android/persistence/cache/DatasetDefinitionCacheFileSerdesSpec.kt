package stasis.test.client_android.persistence.cache

import okio.ByteString.Companion.decodeBase64
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.utils.Try
import stasis.client_android.persistence.cache.DatasetDefinitionCacheFileSerdes
import java.time.Duration
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class DatasetDefinitionCacheFileSerdesSpec {
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

    @Test
    fun serializeKeys() {
        val key = "256daa1a-59ec-408c-8b0b-42909e01250e"
        assertThat(DatasetDefinitionCacheFileSerdes.serializeKey(UUID.fromString(key)), equalTo(key))
    }

    @Test
    fun deserializeKeys() {
        val key = UUID.fromString("256daa1a-59ec-408c-8b0b-42909e01250e")
        assertThat(DatasetDefinitionCacheFileSerdes.deserializeKey(key.toString()), equalTo(key))
    }

    @Test
    fun serializeValues() {
        assertThat(
            DatasetDefinitionCacheFileSerdes.serializeValue(definition),
            equalTo(serializedDefinition.decodeBase64()?.toByteArray())
        )
    }

    @Test
    fun deserializeValues() {
        assertThat(
            DatasetDefinitionCacheFileSerdes.deserializeValue(serializedDefinition.decodeBase64()!!.toByteArray()),
            equalTo(Try.Success(definition))
        )
    }
}
