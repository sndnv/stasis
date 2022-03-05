package stasis.client_android.lib.compression.internal

import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import java.io.OutputStream

class BaseCompressor(
    private val source: BufferedSource,
    createOutput: (Buffer) -> OutputStream
) : Source {
    private val compressed = Buffer()
    private val output = createOutput(compressed)
    private val buffer = Buffer()

    override fun close() {
        output.close()
        source.close()
    }

    override fun read(sink: Buffer, byteCount: Long): Long {
        while (compressed.size < byteCount && source.isOpen && !source.exhausted()) {
            source.read(buffer, byteCount)
            output.write(buffer.readByteArray())
        }

        output.flush()

        if (source.exhausted()) {
            output.close()
        }

        return compressed.read(sink, byteCount)
    }

    override fun timeout(): Timeout =
        source.timeout()
}
