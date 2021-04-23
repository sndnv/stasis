package stasis.client_android.lib.utils

import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer

class FlatMapSource(
    private val source: BufferedSource,
    private val f: (Buffer) -> List<Buffer>
) : Source {
    override fun close() =
        source.close()

    override fun read(sink: Buffer, byteCount: Long): Long {
        require(byteCount >= 0L) { "Invalid byteCount requested: [$byteCount]" }
        if (byteCount == 0L) return 0L

        val buffer = Buffer()
        val bytesRead = source.read(buffer, byteCount)

        return if (bytesRead > 0) {
            f(buffer).map { it.read(sink, it.size) }.sum()
        } else {
            bytesRead
        }
    }

    override fun timeout(): Timeout =
        source.timeout()

    companion object {
        fun BufferedSource.flatMap(f: (Buffer) -> List<Buffer>): BufferedSource =
            FlatMapSource(source = this, f = f).buffer()

        fun BufferedSource.map(f: (Buffer) -> Buffer): BufferedSource =
            this.flatMap { listOf(f(it)) }
    }
}
