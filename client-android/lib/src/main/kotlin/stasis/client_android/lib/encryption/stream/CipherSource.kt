package stasis.client_android.lib.encryption.stream

import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import javax.crypto.Cipher

class CipherSource(
    private val source: BufferedSource,
    private val cipher: Cipher
) : Source {
    private var finalized: Boolean = false
    private val processed = Buffer()

    override fun read(sink: Buffer, byteCount: Long): Long {
        require(byteCount >= 0L) { "Invalid byteCount requested: [$byteCount]" }

        if (byteCount == 0L) return 0L

        if (!finalized) {
            val buffer = Buffer()
            var read = 0L

            while (!source.exhausted() && read < byteCount) {
                source.read(buffer, byteCount)
                val currentProcessed = cipher.update(buffer.readByteArray())

                read += currentProcessed.size
                processed.write(currentProcessed)
            }

            if (source.exhausted()) {
                finalized = true
                processed.write(cipher.doFinal())
            }
        }

        return processed.read(sink, processed.size)
    }

    override fun close() = source.close()

    override fun timeout(): Timeout = source.timeout()

    companion object {
        fun Source.cipherSource(cipher: Cipher): CipherSource =
            CipherSource(source = this.buffer(), cipher = cipher)
    }
}
