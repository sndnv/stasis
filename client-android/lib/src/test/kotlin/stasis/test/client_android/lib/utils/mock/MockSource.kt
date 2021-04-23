package stasis.test.client_android.lib.utils.mock

import okio.Buffer
import okio.Source
import okio.Timeout
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.min

class MockSource(private val maxSize: Long) : Source {
    private var consumed: Long = 0

    override fun close() {
        consumed = maxSize
    }

    override fun read(sink: Buffer, byteCount: Long): Long {
        val rnd = ThreadLocalRandom.current()

        val maxAllowed = maxSize - consumed

        return if (maxAllowed > 0) {
            val amount = min(byteCount, maxAllowed)
            consumed += amount

            val buffer = ByteArray(size = amount.toInt())
            rnd.nextBytes(buffer)
            Buffer().write(buffer).read(sink, amount)
        } else {
            -1
        }
    }

    override fun timeout(): Timeout =
        Timeout.NONE
}
