package stasis.client_android.lib.utils

import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import java.util.LinkedList
import java.util.Queue

class ConcatSource(
    sources: List<suspend () -> Source>,
    private val onSourceConsumed: () -> Unit
) : Source {
    private var queue: Queue<suspend () -> BufferedSource>
    private var active: BufferedSource? = null
    private var isClosed: Boolean = false

    init {
        require(sources.isNotEmpty()) { "At least one source is required" }
        queue = LinkedList(sources.map { suspend { it().buffer() } })
    }

    override fun close() {
        isClosed = true
        queue.clear()
        active?.close()
        active = null
    }

    override fun read(sink: Buffer, byteCount: Long): Long {
        require(byteCount >= 0L) { "Invalid byteCount requested: [$byteCount]" }
        check(!isClosed) { "Source is already closed" }

        if (byteCount == 0L) return 0L

        fun readNextBytes(count: Long, buffer: Buffer): Long {
            val next = when (val actual = active) {
                null -> runBlocking { queue.poll()?.invoke() }.also { active = it } ?: return -1
                else -> actual
            }

            val bytesRead = next.read(buffer, count)

            return when {
                bytesRead == count -> {
                    bytesRead
                }
                bytesRead > 0 -> {
                    val remaining = count - bytesRead
                    active?.close()
                    active = null
                    onSourceConsumed()
                    val additionalBytesRead = readNextBytes(remaining, buffer)
                    if (additionalBytesRead > 0) bytesRead + additionalBytesRead else bytesRead
                }
                else -> {
                    active?.close()
                    active = null
                    onSourceConsumed()
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
        active?.timeout() ?: Timeout.NONE
}
