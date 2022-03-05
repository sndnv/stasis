package stasis.test.client_android.serialization

import okio.ByteString.Companion.toByteString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import stasis.client_android.serialization.ByteStrings.decodeFromBase64
import stasis.client_android.serialization.ByteStrings.encodeAsBase64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class ByteStringsSpec {
    @Test
    fun convertByteStringsToAndFromBase64() {
        val original = "test".toByteArray().toByteString()

        val encoded = original.encodeAsBase64()
        val decoded = encoded.decodeFromBase64()

        assertThat(encoded, equalTo("dGVzdA"))
        assertThat(decoded, equalTo(original))
    }
}
