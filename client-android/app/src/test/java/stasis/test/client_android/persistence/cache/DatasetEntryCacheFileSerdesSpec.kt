package stasis.test.client_android.persistence.cache

import okio.ByteString.Companion.decodeBase64
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import stasis.client_android.lib.model.server.datasets.DatasetEntry
import stasis.client_android.lib.utils.Try
import stasis.client_android.persistence.cache.DatasetEntryCacheFileSerdes
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class DatasetEntryCacheFileSerdesSpec {
    private val datasetEntry = DatasetEntry(
        id = UUID.fromString("71a357e7-856f-4ae1-9d4d-abf424daddcd"),
        definition = UUID.fromString("03455212-735b-444a-9886-6762432ebae9"),
        device = UUID.fromString("0fabfb8e-aa45-4e9e-bca1-cca20b09097d"),
        data = setOf(
            UUID.fromString("87f08176-958e-411d-913e-b29798053030"),
            UUID.fromString("0b2ca24f-f029-4733-8a73-8f835245d110"),
            UUID.fromString("4cc26129-8921-4c1b-a207-f6b0edc1b4ee")
        ),
        metadata = UUID.fromString("56179ef2-7897-488f-9cf0-6aa08c57b259"),
        created = Instant.parse("2020-01-02T03:04:05Z")
    )

    private val serializedDatasetEntry =
        "ChUI4ZW9q/j81dFxEM2766bC/uqmnQESFQjKiO2ap8LU" +
                "ogMQ6fW6maTsmcOYARoVCJ6dldLq8f7VDxD9kqTYoJ" +
                "Tz0LwBIhYInYK5rOmuoPiHARCw4JTA+dKsn5EBIhUI" +
                "s46lgf/JqJYLEJCil5K18OO5igEiFQibmIXJmKWY4U" +
                "wQ7umG7o7W/YOiASoVCI+R3cSn3ueLVhDZ5N7iiNSa" +
                "+JwBMIiZ16H2LQ=="

    @Test
    fun serializeKeys() {
        val key = "256daa1a-59ec-408c-8b0b-42909e01250e"
        assertThat(DatasetEntryCacheFileSerdes.serializeKey(UUID.fromString(key)), equalTo(key))
    }

    @Test
    fun deserializeKeys() {
        val key = UUID.fromString("256daa1a-59ec-408c-8b0b-42909e01250e")
        assertThat(DatasetEntryCacheFileSerdes.deserializeKey(key.toString()), equalTo(key))
    }

    @Test
    fun serializeValues() {
        assertThat(
            DatasetEntryCacheFileSerdes.serializeValue(datasetEntry),
            equalTo(serializedDatasetEntry.decodeBase64()?.toByteArray())
        )
    }

    @Test
    fun deserializeValues() {
        assertThat(
            DatasetEntryCacheFileSerdes.deserializeValue(serializedDatasetEntry.decodeBase64()!!.toByteArray()),
            equalTo(Try.Success(datasetEntry))
        )
    }
}
