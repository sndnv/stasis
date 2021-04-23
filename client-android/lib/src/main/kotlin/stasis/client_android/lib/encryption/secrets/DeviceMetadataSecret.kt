package stasis.client_android.lib.encryption.secrets

import okio.ByteString
import okio.Source
import stasis.client_android.lib.encryption.Aes

data class DeviceMetadataSecret(
    val iv: ByteString,
    private val key: ByteString
) : Secret() {
    fun encryption(source: Source): Source = Aes.encryption(source, key, iv)
    fun decryption(source: Source): Source = Aes.decryption(source, key, iv)
}
