package stasis.client_android.lib.encryption

import okio.ByteString
import okio.Sink
import okio.Source
import stasis.client_android.lib.encryption.secrets.DeviceFileSecret
import stasis.client_android.lib.encryption.secrets.DeviceMetadataSecret
import stasis.client_android.lib.encryption.stream.CipherTransformation.process
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object Aes : Encoder, Decoder {
    // recommended IV size for GCM (96 bits); for more info see https://crypto.stackexchange.com/a/41610
    const val IvSize: Int = 12 // bytes

    // maximum tag size; for more info see javax.crypto.spec.GCMParameterSpec
    const val TagSize: Int = 128 // bits

    private const val MaxAesPlaintextSize: Long = 4L * 1024 * 1024 * 1024

    // various suggestions exist about the max plaintext size for GCM;
    // the limit here is set as 4 GB, well below all suggested maximum sizes
    // for more info see https://crypto.stackexchange.com/q/31793 and https://crypto.stackexchange.com/q/44113
    override val maxPlaintextSize: Long = MaxAesPlaintextSize

    override fun encrypt(source: Source, fileSecret: DeviceFileSecret): Source =
        fileSecret.encryption(source)

    override fun encrypt(sink: Sink, fileSecret: DeviceFileSecret): Sink =
        fileSecret.encryption(sink)

    override fun encrypt(source: Source, metadataSecret: DeviceMetadataSecret): Source =
        metadataSecret.encryption(source)

    override fun decrypt(source: Source, fileSecret: DeviceFileSecret): Source =
        fileSecret.decryption(source)

    override fun decrypt(source: Source, metadataSecret: DeviceMetadataSecret): Source =
        metadataSecret.decryption(source)

    fun encryption(source: Source, key: ByteString, iv: ByteString): Source = process(
        source = source,
        algorithm = "AES",
        cipherMode = "GCM",
        padding = "NoPadding",
        operationMode = Cipher.ENCRYPT_MODE,
        key = SecretKeySpec(key.toByteArray(), "AES"),
        spec = GCMParameterSpec(TagSize, iv.toByteArray())
    )

    fun encryption(sink: Sink, key: ByteString, iv: ByteString): Sink = process(
        sink = sink,
        algorithm = "AES",
        cipherMode = "GCM",
        padding = "NoPadding",
        operationMode = Cipher.ENCRYPT_MODE,
        key = SecretKeySpec(key.toByteArray(), "AES"),
        spec = GCMParameterSpec(TagSize, iv.toByteArray())
    )

    fun decryption(source: Source, key: ByteString, iv: ByteString): Source = process(
        source = source,
        algorithm = "AES",
        cipherMode = "GCM",
        padding = "NoPadding",
        operationMode = Cipher.DECRYPT_MODE,
        key = SecretKeySpec(key.toByteArray(), "AES"),
        spec = GCMParameterSpec(TagSize, iv.toByteArray())
    )
}
