package stasis.client_android.lib.encryption.stream

import okio.Sink
import okio.Source
import okio.cipherSink
import okio.cipherSource
import java.security.Key
import java.security.spec.AlgorithmParameterSpec
import javax.crypto.Cipher

object CipherTransformation {
    fun process(
        source: Source,
        algorithm: String,
        cipherMode: String,
        padding: String,
        operationMode: Int,
        key: Key,
        spec: AlgorithmParameterSpec?
    ): Source {
        val cipher = Cipher.getInstance("$algorithm/$cipherMode/$padding")

        when (spec) {
            null -> cipher.init(operationMode, key)
            else -> cipher.init(operationMode, key, spec)
        }

        return source.cipherSource(cipher)
    }

    fun process(
        sink: Sink,
        algorithm: String,
        cipherMode: String,
        padding: String,
        operationMode: Int,
        key: Key,
        spec: AlgorithmParameterSpec?
    ): Sink {
        val cipher = Cipher.getInstance("$algorithm/$cipherMode/$padding")

        when (spec) {
            null -> cipher.init(operationMode, key)
            else -> cipher.init(operationMode, key, spec)
        }

        return sink.cipherSink(cipher)
    }
}
