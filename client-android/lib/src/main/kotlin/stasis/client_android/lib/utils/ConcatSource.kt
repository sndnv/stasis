package stasis.client_android.lib.utils

import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import java.util.LinkedList
import java.util.Queue

class ConcatSource(
    sources: List<Source>
) : Source {
    private var queue: Queue<BufferedSource>

    init {
        require(sources.isNotEmpty()) { "At least one source is required" }
        queue = LinkedList(sources.map { it.buffer() })
    }

    override fun close() {
        queue.forEach { source -> source.close() }
    }

    override fun read(sink: Buffer, byteCount: Long): Long {
        require(byteCount >= 0L) { "Invalid byteCount requested: [$byteCount]" }
        if (byteCount == 0L) return 0L

        fun readNextBytes(count: Long, buffer: Buffer): Long {
            val next: BufferedSource = queue.peek() ?: return -1

            val bytesRead = next.read(buffer, count)

            return when {
                bytesRead == count -> {
                    bytesRead
                }
                bytesRead > 0 -> {
                    val remaining = count - bytesRead
                    queue.remove().close()
                    val additionalBytesRead = readNextBytes(remaining, buffer)
                    if (additionalBytesRead > 0) bytesRead + additionalBytesRead else bytesRead
                }
                else -> {
                    queue.remove().close()
                    readNextBytes(count, buffer)
                }
            }
        }

        val buffer = Buffer()
        val bytesRead = readNextBytes(byteCount, buffer)

        return if (bytesRead > 0) {
            buffer.read(sink, buffer.size)
        } else {
            bytesRead
        }
    }

    override fun timeout(): Timeout =
        queue.peek()?.timeout() ?: Timeout.NONE
}
