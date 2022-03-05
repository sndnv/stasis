package stasis.client_android.lib.encryption.stream

import okio.Buffer
import okio.Sink
import okio.Timeout
import javax.crypto.Cipher

class CipherSink(
    private val sink: Sink,
    private val cipher: Cipher
) : Sink {
    private var finalized: Boolean = false
    private val processed = Buffer()

    override fun write(source: Buffer, byteCount: Long) {
        require(byteCount >= 0L) { "Invalid byteCount requested: [$byteCount]" }

        if (!finalized) {
            val original = Buffer().let { buffer ->
                source.read(buffer, byteCount)
                buffer.readByteArray()
            }

            processed.write(
                if (!source.isOpen) {
                    finalized = true
                    cipher.doFinal(original)
                } else {
                    cipher.update(original)
                }
            )
        }

        sink.write(processed, processed.size)
    }

    override fun close() {
        if(!finalized) {
            processed.write(cipher.doFinal())
            sink.write(processed, processed.size)
        }

        sink.close()
    }

    override fun flush() = Unit // do nothing

    override fun timeout(): Timeout = sink.timeout()

    companion object {
        fun Sink.cipherSink(cipher: Cipher): CipherSink =
            CipherSink(sink = this, cipher = cipher)
    }
}
