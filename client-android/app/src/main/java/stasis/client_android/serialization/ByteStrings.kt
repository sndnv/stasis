package stasis.client_android.serialization

import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.*

object ByteStrings {
    fun ByteString.encodeAsBase64(): String = Base64
        .getUrlEncoder()
        .withoutPadding()
        .encodeToString(this.toByteArray())

    fun String.decodeFromBase64(): ByteString =
        Base64.getUrlDecoder().decode(this).toByteString()
}
