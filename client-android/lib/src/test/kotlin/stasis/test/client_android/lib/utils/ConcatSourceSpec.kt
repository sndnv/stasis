package stasis.test.client_android.lib.utils

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okio.Buffer
import okio.buffer
import stasis.client_android.lib.utils.ConcatSource
import stasis.client_android.lib.utils.FlatMapSource.Companion.map
import stasis.test.client_android.lib.utils.mock.MockSource
import java.util.concurrent.atomic.AtomicInteger

class ConcatSourceSpec : WordSpec({
    "ConcatSource" should {
        "support reading data from a single source" {
            val sourcesConsumed = AtomicInteger(0)

            val source = ConcatSource(
                sources = listOf(suspend { Buffer().write("test".toByteArray()) }),
                onSourceConsumed = { sourcesConsumed.incrementAndGet() }
            )

            source.buffer().readByteString().utf8() shouldBe ("test")
            sourcesConsumed.get() shouldBe (1)
        }

        "support reading data from a single source (large payloads)" {
            val sourcesConsumed = AtomicInteger(0)

            val originalSize: Long = 256 * 1024
            val source = ConcatSource(
                sources = listOf(suspend { MockSource(maxSize = originalSize).buffer() }),
                onSourceConsumed = { sourcesConsumed.incrementAndGet() }
            )

            val updated = source.buffer().map {
                val bytes = it.readByteArray()
                Buffer().write(bytes).write(bytes)
            }

            updated.readByteArray().size shouldBe (2 * originalSize)
            sourcesConsumed.get() shouldBe (1)
        }

        "support reading data from multiple sources" {
            val sourcesConsumed = AtomicInteger(0)

            val source = ConcatSource(
                sources = listOf(
                    suspend { Buffer().write("test1".toByteArray()) },
                    suspend { Buffer().write("test2".toByteArray()) },
                    suspend { Buffer().write("test3".toByteArray()) },
                    suspend { Buffer().write("test4".toByteArray()) },
                    suspend { Buffer().write("test5".toByteArray()) }
                ),
                onSourceConsumed = { sourcesConsumed.incrementAndGet() }
            )

            source.buffer().readByteString().utf8() shouldBe ("test1test2test3test4test5")
            sourcesConsumed.get() shouldBe (5)
        }

        "support reading data from multiple sources (large payloads)" {
            val sourcesConsumed = AtomicInteger(0)

            val originalSize: Long = 256 * 1024

            val source = ConcatSource(
                sources = listOf(
                    suspend { MockSource(maxSize = originalSize * 1).buffer() },
                    suspend { MockSource(maxSize = originalSize * 2).buffer() },
                    suspend { MockSource(maxSize = originalSize * 3).buffer() },
                    suspend { MockSource(maxSize = originalSize * 4).buffer() },
                    suspend { MockSource(maxSize = originalSize * 5).buffer() }
                ),
                onSourceConsumed = { sourcesConsumed.incrementAndGet() }
            )

            source.buffer().readByteString().size shouldBe (originalSize * 15)
            sourcesConsumed.get() shouldBe (5)
        }

        "fail if an invalid byte count is requested" {
            val sourcesConsumed = AtomicInteger(0)

            val source = ConcatSource(
                sources = listOf(suspend { Buffer().write("test".toByteArray()) }),
                onSourceConsumed = { sourcesConsumed.incrementAndGet() }
            )

            val e = shouldThrow<IllegalArgumentException> {
                source.read(Buffer(), byteCount = -1)
            }

            e.message shouldBe ("Invalid byteCount requested: [-1]")
            sourcesConsumed.get() shouldBe (0)
        }

        "do nothing if no bytes are requested" {
            val sourcesConsumed = AtomicInteger(0)

            val source = ConcatSource(
                sources = listOf(suspend { Buffer().write("test".toByteArray()) }),
                onSourceConsumed = { sourcesConsumed.incrementAndGet() }
            )

            val bytesRead = source.read(Buffer(), byteCount = 0)

            bytesRead shouldBe (0)
        }

        "fail if no sources are provided" {
            val sourcesConsumed = AtomicInteger(0)

            val e = shouldThrow<IllegalArgumentException> {
                ConcatSource(
                    sources = emptyList(),
                    onSourceConsumed = { sourcesConsumed.incrementAndGet() }
                )
            }

            e.message shouldBe ("At least one source is required")
            sourcesConsumed.get() shouldBe (0)
        }

        "close all sources when it is itself closed" {
            val sourcesConsumed = AtomicInteger(0)

            val source = ConcatSource(
                sources = listOf(suspend { Buffer().write("test".toByteArray()) }),
                onSourceConsumed = { sourcesConsumed.incrementAndGet() }
            )

            source.read(Buffer(), byteCount = 1) shouldBe (1)

            source.close()

            val e = shouldThrow<IllegalStateException> {
                source.read(Buffer(), byteCount = 1024)
            }

            e.message shouldBe ("Source is already closed")
            sourcesConsumed.get() shouldBe (0)
        }
    }
})
