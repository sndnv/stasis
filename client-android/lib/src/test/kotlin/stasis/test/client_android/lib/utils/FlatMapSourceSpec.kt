package stasis.test.client_android.lib.utils

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okio.Buffer
import okio.buffer
import stasis.client_android.lib.utils.FlatMapSource
import stasis.client_android.lib.utils.FlatMapSource.Companion.flatMap
import stasis.client_android.lib.utils.FlatMapSource.Companion.map
import stasis.test.client_android.lib.utils.mock.MockSource

class FlatMapSourceSpec : WordSpec({
    "FlatMapSource" should {
        "support flat-mapping" {
            val source = Buffer().write("tESt".toByteArray())

            val updated = source
                .flatMap {
                    val original = it.readByteString().utf8()
                    val lowercase = original.toLowerCase().toByteArray()
                    val uppercase = original.toUpperCase().toByteArray()

                    val separator = ",".toByteArray()

                    listOf(
                        Buffer().write(original.toByteArray()),
                        Buffer().write(separator),
                        Buffer().write(lowercase),
                        Buffer().write(separator),
                        Buffer().write(uppercase),
                    )
                }

            updated.readByteString().utf8() shouldBe ("tESt,test,TEST")
        }

        "support mapping" {
            val source = Buffer().write("test".toByteArray())

            val updated = source
                .map {
                    val original = it.readByteString().utf8()
                    val updated = original.toUpperCase().toByteArray()

                    Buffer().write(updated)
                }

            updated.readByteString().utf8() shouldBe ("TEST")
        }

        "support mapping (large payloads)" {
            val originalSize: Long = 256 * 1024
            val source = MockSource(maxSize = originalSize).buffer()

            val updated = source.map {
                val bytes = it.readByteArray()
                Buffer().write(bytes).write(bytes)
            }

            updated.readByteArray().size shouldBe (2 * originalSize)
        }

        "fail if an invalid byte count is requested" {
            val source = FlatMapSource(Buffer().write("test".toByteArray()), f = { listOf(it) })

            val e = shouldThrow<IllegalArgumentException> {
                source.read(Buffer(), byteCount = -1)
            }

            e.message shouldBe ("Invalid byteCount requested: [-1]")
        }

        "do nothing if no bytes are requested" {
            val source = FlatMapSource(Buffer().write("test".toByteArray()), f = { listOf(it) })

            val bytesRead = source.read(Buffer(), byteCount = 0)

            bytesRead shouldBe (0)
        }
    }
})
