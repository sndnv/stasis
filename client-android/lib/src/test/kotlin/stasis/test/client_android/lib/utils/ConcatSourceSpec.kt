package stasis.test.client_android.lib.utils

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okio.Buffer
import okio.buffer
import stasis.client_android.lib.utils.ConcatSource
import stasis.client_android.lib.utils.FlatMapSource.Companion.map
import stasis.test.client_android.lib.utils.mock.MockSource

class ConcatSourceSpec : WordSpec({
    "ConcatSource" should {
        "support reading data from a single source" {
            val source = ConcatSource(sources = listOf(suspend { Buffer().write("test".toByteArray()) }))

            source.buffer().readByteString().utf8() shouldBe ("test")
        }

        "support reading data from a single source (large payloads)" {
            val originalSize: Long = 256 * 1024
            val source = ConcatSource(sources = listOf(suspend { MockSource(maxSize = originalSize).buffer() }))

            val updated = source.buffer().map {
                val bytes = it.readByteArray()
                Buffer().write(bytes).write(bytes)
            }

            updated.readByteArray().size shouldBe (2 * originalSize)
        }

        "support reading data from multiple sources" {
            val source = ConcatSource(
                sources = listOf(
                    suspend { Buffer().write("test1".toByteArray()) },
                    suspend { Buffer().write("test2".toByteArray()) },
                    suspend { Buffer().write("test3".toByteArray()) },
                    suspend { Buffer().write("test4".toByteArray()) },
                    suspend { Buffer().write("test5".toByteArray()) }
                )
            )

            source.buffer().readByteString().utf8() shouldBe ("test1test2test3test4test5")
        }

        "support reading data from multiple sources (large payloads)" {
            val originalSize: Long = 256 * 1024

            val source = ConcatSource(
                sources = listOf(
                    suspend { MockSource(maxSize = originalSize * 1).buffer() },
                    suspend { MockSource(maxSize = originalSize * 2).buffer() },
                    suspend { MockSource(maxSize = originalSize * 3).buffer() },
                    suspend { MockSource(maxSize = originalSize * 4).buffer() },
                    suspend { MockSource(maxSize = originalSize * 5).buffer() }
                )
            )

            source.buffer().readByteString().size shouldBe (originalSize * 15)
        }

        "fail if an invalid byte count is requested" {
            val source = ConcatSource(sources = listOf(suspend { Buffer().write("test".toByteArray()) }))

            val e = shouldThrow<IllegalArgumentException> {
                source.read(Buffer(), byteCount = -1)
            }

            e.message shouldBe ("Invalid byteCount requested: [-1]")
        }

        "do nothing if no bytes are requested" {
            val source = ConcatSource(sources = listOf(suspend { Buffer().write("test".toByteArray()) }))

            val bytesRead = source.read(Buffer(), byteCount = 0)

            bytesRead shouldBe (0)
        }

        "fail if no sources are provided" {
            val e = shouldThrow<IllegalArgumentException> {
                ConcatSource(sources = emptyList())
            }

            e.message shouldBe ("At least one source is required")
        }

        "close all sources when it is itself closed" {
            val source = ConcatSource(sources = listOf(suspend { Buffer().write("test".toByteArray()) }))

            source.read(Buffer(), byteCount = 1) shouldBe (1)

            source.close()

            val e = shouldThrow<IllegalStateException> {
                source.read(Buffer(), byteCount = 1024)
            }

            e.message shouldBe ("Source is already closed")
        }
    }
})
