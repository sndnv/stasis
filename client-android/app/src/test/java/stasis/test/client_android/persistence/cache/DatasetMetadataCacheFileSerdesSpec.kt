package stasis.test.client_android.persistence.cache

import okio.ByteString.Companion.decodeBase64
import org.hamcrest.CoreMatchers.anyOf
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
import stasis.test.client_android.Fixtures
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class DatasetMetadataCacheFileSerdesSpec {
    private val datasetMetadata = DatasetMetadata(
        contentChanged = mapOf(
            Fixtures.Metadata.FileOneMetadata.path to Fixtures.Metadata.FileOneMetadata
        ),
        metadataChanged = mapOf(
            Fixtures.Metadata.FileTwoMetadata.path to Fixtures.Metadata.FileTwoMetadata,
            Fixtures.Metadata.FileThreeMetadata.path to Fixtures.Metadata.FileThreeMetadata
        ),
        filesystem = FilesystemMetadata(
            entities = mapOf(
                Fixtures.Metadata.FileOneMetadata.path to FilesystemMetadata.EntityState.New,
                Fixtures.Metadata.FileTwoMetadata.path to FilesystemMetadata.EntityState.New,
                Fixtures.Metadata.FileThreeMetadata.path to FilesystemMetadata.EntityState.Updated
            )
        )
    )

    private val compressedDatasetMetadata11 =
        "H4sIAAAAAAAAAOJqYuTi1S/JLdBPy8xJ1c/PSxUq5MpH" +
                "ExJg1GhYMWFx46Mfx/8zGvz/ePXK+lntFlYsRfn5JU" +
                "5g0ouzqLwCgoIYGaM0uPhRDIg3EBLl2NHa27rj777z" +
                "RgKnvq/8f2D7vHcbGZNY8kBWTkNxREl5vlALI1cjuq" +
                "AAkxSSsSUZRampGjDHGCAciNNdWlGayO4Cmgh0lxhH" +
                "S9vVKw9Xrdr5jFFg188J7f3NS390AR2WXpVZINTByM" +
                "WH0JGWX1okVMZVgi4mwESSOxiA7hBANQEcQE+bJ66a" +
                "9XT7mRqBNfOX7plxfOK1qYxJ7CmpaTmJJalSDlzC6D" +
                "HFxMWAKggKOZCgCIarmaQYAAAAAP//AwAO6eeE6wEA" +
                "AA=="

    private val compressedDatasetMetadata17 =
        "H4sIAAAAAAAA/+JqYuTi1S/JLdBPy8xJ1c/PSxUq5MpH" +
                "ExJg1GhYMWFx46Mfx/8zGvz/ePXK+lntFlYsRfn5JU" +
                "5g0ouzqLwCgoIYGaM0uPhRDIg3EBLl2NHa27rj777z" +
                "RgKnvq/8f2D7vHcbGZNY8kBWTkNxREl5vlALI1cjuq" +
                "AAkxSSsSUZRampGjDHGCAciNNdWlGayO4Cmgh0lxhH" +
                "S9vVKw9Xrdr5jFFg188J7f3NS390AR2WXpVZINTByM" +
                "WH0JGWX1okVMZVgi4mwESSOxiA7hBANQEcQE+bJ66a" +
                "9XT7mRqBNfOX7plxfOK1qYxJ7CmpaTmJJalSDlzC6D" +
                "HFxMWAKggKOZCgCIarmaQYAAAAAP//AwAO6eeE6wEA" +
                "AA=="

    @Test
    fun serializeKeys() {
        val key = "256daa1a-59ec-408c-8b0b-42909e01250e"
        assertThat(
            DatasetMetadataCacheFileSerdes.serializeKey(UUID.fromString(key)),
            equalTo(key)
        )
    }

    @Test
    fun deserializeKeys() {
        val key = UUID.fromString("256daa1a-59ec-408c-8b0b-42909e01250e")
        assertThat(
            DatasetMetadataCacheFileSerdes.deserializeKey(key.toString()),
            equalTo(key)
        )
    }

    @Test
    fun serializeValues() {
        assertThat(
            DatasetMetadataCacheFileSerdes.serializeValue(datasetMetadata),
            anyOf(
                equalTo(compressedDatasetMetadata11.decodeBase64()?.toByteArray()),
                equalTo(compressedDatasetMetadata17.decodeBase64()?.toByteArray())
            )
        )
    }

    @Test
    fun deserializeValues() {
        val deserialized11 =
            DatasetMetadataCacheFileSerdes.deserializeValue(compressedDatasetMetadata11.decodeBase64()!!.toByteArray())
        assertThat(deserialized11, equalTo(Try.Success(datasetMetadata)))

        val deserialized17 =
            DatasetMetadataCacheFileSerdes.deserializeValue(compressedDatasetMetadata17.decodeBase64()!!.toByteArray())
        assertThat(deserialized17, equalTo(Try.Success(datasetMetadata)))
    }
}
