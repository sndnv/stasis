package stasis.test.client_android.persistence.cache

import okio.ByteString.Companion.decodeBase64
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import stasis.client_android.lib.api.clients.caching.DatasetEntriesForDefinition
import stasis.client_android.lib.utils.Try
import stasis.client_android.persistence.cache.DatasetEntriesForDefinitionCacheFileSerdes
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class DatasetEntriesForDefinitionCacheFileSerdesSpec {
    val entry = DatasetEntriesForDefinition(
        entries = mapOf(
            UUID.fromString("03455212-735b-444a-9886-6762432ebae9") to java.time.Instant.parse("2020-01-02T03:04:05Z"),
            UUID.fromString("87f08176-958e-411d-913e-b29798053030") to java.time.Instant.parse("2020-01-03T03:04:05Z"),
            UUID.fromString("4cc26129-8921-4c1b-a207-f6b0edc1b4ee") to java.time.Instant.parse("2020-01-04T03:04:05Z"),
        ),
        latest = UUID.fromString("03455212-735b-444a-9886-6762432ebae9")
    )

    val serializedEntry =
        "Ci0KJDAzNDU1MjEyLTczNWItNDQ0YS05ODg2LTY3NjI0" +
                "MzJlYmFlORCImdeh9i0KLQokODdmMDgxNzYtOTU4ZS" +
                "00MTFkLTkxM2UtYjI5Nzk4MDUzMDMwEIjR8Mr2LQot" +
                "CiQ0Y2MyNjEyOS04OTIxLTRjMWItYTIwNy1mNmIwZW" +
                "RjMWI0ZWUQiImK9PYtEiQwMzQ1NTIxMi03MzViLTQ0" +
                "NGEtOTg4Ni02NzYyNDMyZWJhZTk="

    @Test
    fun serializeKeys() {
        val key = "256daa1a-59ec-408c-8b0b-42909e01250e"
        assertThat(DatasetEntriesForDefinitionCacheFileSerdes.serializeKey(UUID.fromString(key)), equalTo(key))
    }

    @Test
    fun deserializeKeys() {
        val key = UUID.fromString("256daa1a-59ec-408c-8b0b-42909e01250e")
        assertThat(DatasetEntriesForDefinitionCacheFileSerdes.deserializeKey(key.toString()), equalTo(key))
    }

    @Test
    fun serializeValues() {
        assertThat(
            DatasetEntriesForDefinitionCacheFileSerdes.serializeValue(entry),
            equalTo(serializedEntry.decodeBase64()?.toByteArray())
        )
    }

    @Test
    fun deserializeValues() {
        assertThat(
            DatasetEntriesForDefinitionCacheFileSerdes.deserializeValue(serializedEntry.decodeBase64()!!.toByteArray()),
            equalTo(Try.Success(entry))
        )
    }
}
