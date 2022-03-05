package stasis.test.client_android.lib.utils

import io.kotest.assertions.fail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import stasis.client_android.lib.utils.Reference
import java.util.concurrent.atomic.AtomicInteger

class ReferenceSpec : WordSpec({
    data class Config(val size: Int)
    data class Singleton(val content: String)

    "A Singleton Reference" should {
        "support caching instances and provide them as objects" {
            val retrieveConfigCount = AtomicInteger(0)
            val createCount = AtomicInteger(0)
            val destroyCount = AtomicInteger(0)

            val expectedConfig = Config(size = 42)

            val ref = Reference.Singleton(
                retrieveConfig = {
                    retrieveConfigCount.incrementAndGet()
                    expectedConfig
                },
                create = { actualConfig ->
                    createCount.incrementAndGet()
                    actualConfig shouldBe (expectedConfig)

                    Singleton(content = List(actualConfig.size) { it.toChar() }.joinToString(separator = ""))
                },
                destroy = {
                    destroyCount.incrementAndGet()
                }
            )

            ref.isEmpty() shouldBe (true)
            ref.isNotEmpty() shouldBe (false)

            ref.provided()?.content?.length shouldBe (expectedConfig.size)
            retrieveConfigCount.get() shouldBe (1)
            createCount.get() shouldBe (1)
            destroyCount.get() shouldBe (0)

            ref.provided()?.content?.length shouldBe (expectedConfig.size)
            ref.provided()?.content?.length shouldBe (expectedConfig.size)
            ref.provided()?.content?.length shouldBe (expectedConfig.size)

            retrieveConfigCount.get() shouldBe (4)
            createCount.get() shouldBe (1)
            destroyCount.get() shouldBe (0)

            ref.isEmpty() shouldBe (false)
            ref.isNotEmpty() shouldBe (true)
        }

        "support caching instances and provide them as context" {
            val retrieveConfigCount = AtomicInteger(0)
            val createCount = AtomicInteger(0)
            val destroyCount = AtomicInteger(0)

            val expectedConfig = Config(size = 42)

            val ref = Reference.Singleton(
                retrieveConfig = {
                    retrieveConfigCount.incrementAndGet()
                    expectedConfig
                },
                create = { actualConfig ->
                    createCount.incrementAndGet()
                    actualConfig shouldBe (expectedConfig)

                    Singleton(content = List(actualConfig.size) { it.toChar() }.joinToString(separator = ""))
                },
                destroy = {
                    destroyCount.incrementAndGet()
                }
            )

            ref.provided { it.content }?.length shouldBe (expectedConfig.size)
            retrieveConfigCount.get() shouldBe (1)
            createCount.get() shouldBe (1)
            destroyCount.get() shouldBe (0)

            ref.provided { it.content }?.length shouldBe (expectedConfig.size)
            ref.provided { it.content }?.length shouldBe (expectedConfig.size)
            ref.provided { it.content }?.length shouldBe (expectedConfig.size)

            retrieveConfigCount.get() shouldBe (4)
            createCount.get() shouldBe (1)
            destroyCount.get() shouldBe (0)
        }

        "support caching instances and provide them as required objects" {
            val retrieveConfigCount = AtomicInteger(0)
            val createCount = AtomicInteger(0)
            val destroyCount = AtomicInteger(0)

            val expectedConfig = Config(size = 42)

            val ref = Reference.Singleton(
                retrieveConfig = {
                    retrieveConfigCount.incrementAndGet()
                    expectedConfig
                },
                create = { actualConfig ->
                    createCount.incrementAndGet()
                    actualConfig shouldBe (expectedConfig)

                    Singleton(content = List(actualConfig.size) { it.toChar() }.joinToString(separator = ""))
                },
                destroy = {
                    destroyCount.incrementAndGet()
                }
            )

            ref.required().content.length shouldBe (expectedConfig.size)
            retrieveConfigCount.get() shouldBe (1)
            createCount.get() shouldBe (1)
            destroyCount.get() shouldBe (0)

            ref.required().content.length shouldBe (expectedConfig.size)
            ref.required().content.length shouldBe (expectedConfig.size)
            ref.required().content.length shouldBe (expectedConfig.size)

            retrieveConfigCount.get() shouldBe (4)
            createCount.get() shouldBe (1)
            destroyCount.get() shouldBe (0)
        }

        "support caching instances and provide them as required context" {
            val retrieveConfigCount = AtomicInteger(0)
            val createCount = AtomicInteger(0)
            val destroyCount = AtomicInteger(0)

            val expectedConfig = Config(size = 42)

            val ref = Reference.Singleton(
                retrieveConfig = {
                    retrieveConfigCount.incrementAndGet()
                    expectedConfig
                },
                create = { actualConfig ->
                    createCount.incrementAndGet()
                    actualConfig shouldBe (expectedConfig)

                    Singleton(content = List(actualConfig.size) { it.toChar() }.joinToString(separator = ""))
                },
                destroy = {
                    destroyCount.incrementAndGet()
                }
            )

            ref.required { it.content }.length shouldBe (expectedConfig.size)
            retrieveConfigCount.get() shouldBe (1)
            createCount.get() shouldBe (1)
            destroyCount.get() shouldBe (0)

            ref.required { it.content }.length shouldBe (expectedConfig.size)
            ref.required { it.content }.length shouldBe (expectedConfig.size)
            ref.required { it.content }.length shouldBe (expectedConfig.size)

            retrieveConfigCount.get() shouldBe (4)
            createCount.get() shouldBe (1)
            destroyCount.get() shouldBe (0)
        }

        "discard existing references when the config is missing" {
            val retrieveConfigCount = AtomicInteger(0)
            val createCount = AtomicInteger(0)
            val destroyCount = AtomicInteger(0)

            val ref = Reference.Singleton(
                retrieveConfig = {
                    val count = retrieveConfigCount.getAndIncrement()

                    if (count == 0) {
                        Config(size = 42)
                    } else {
                        null
                    }
                },
                create = {
                    createCount.incrementAndGet()
                    Singleton(content = "test")
                },
                destroy = {
                    destroyCount.incrementAndGet()
                }
            )

            ref.provided { it.content } shouldNotBe (null)
            retrieveConfigCount.get() shouldBe (1)
            createCount.get() shouldBe (1)
            destroyCount.get() shouldBe (0)

            ref.provided { it.content } shouldBe (null)
            retrieveConfigCount.get() shouldBe (2)
            createCount.get() shouldBe (1)
            destroyCount.get() shouldBe (1)

            val e = shouldThrow<IllegalArgumentException> { ref.required() }
            e.message shouldBe ("Expected result but none was provided")
            retrieveConfigCount.get() shouldBe (3)
            createCount.get() shouldBe (1)
            destroyCount.get() shouldBe (2)
        }

        "support mapping reference instances" {
            val retrieveConfigCount = AtomicInteger(0)
            val outerCreateCount = AtomicInteger(0)
            val innerCreateCount = AtomicInteger(0)
            val destroyCount = AtomicInteger(0)

            val originalContent = "test"
            val mappedContent = originalContent.length

            val originalRef = Reference.Singleton(
                retrieveConfig = {
                    val count = retrieveConfigCount.getAndIncrement()

                    if (count <= 1) {
                        Config(size = 42)
                    } else {
                        null
                    }
                },
                create = {
                    outerCreateCount.incrementAndGet()
                    Singleton(content = originalContent)
                },
                destroy = {
                    destroyCount.incrementAndGet()
                }
            )

            val mappedRef = originalRef.map {
                innerCreateCount.incrementAndGet()
                it.content.length
            }

            originalRef.provided()?.content shouldBe (originalContent)
            retrieveConfigCount.get() shouldBe (1)
            outerCreateCount.get() shouldBe (1)
            innerCreateCount.get() shouldBe (0)
            destroyCount.get() shouldBe (0)

            mappedRef.provided() shouldBe (mappedContent)
            retrieveConfigCount.get() shouldBe (2)
            outerCreateCount.get() shouldBe (1)
            innerCreateCount.get() shouldBe (1)
            destroyCount.get() shouldBe (0)

            originalRef.provided()?.content shouldBe (null)
            retrieveConfigCount.get() shouldBe (3)
            outerCreateCount.get() shouldBe (1)
            innerCreateCount.get() shouldBe (1)
            destroyCount.get() shouldBe (1)

            mappedRef.provided() shouldBe (null)
            retrieveConfigCount.get() shouldBe (4)
            outerCreateCount.get() shouldBe (1)
            innerCreateCount.get() shouldBe (1)
            destroyCount.get() shouldBe (2)
        }

        "fail if no valid instance could be created" {
            val ref = Reference.Singleton(
                retrieveConfig = { Config(size = 42) },
                create = { null },
                destroy = { }
            )

            val e = shouldThrow<IllegalArgumentException> {
                ref.provided<Any> { fail("Expected failure but none was encountered") }
            }

            e.message shouldBe ("Expected an object but none was provided")
        }

        "support providing itself when a new singleton is requested" {
            val retrieveConfigCount = AtomicInteger(0)
            val createCount = AtomicInteger(0)
            val destroyCount = AtomicInteger(0)

            val originalContent = "test"

            val existingRef = Reference.Singleton(
                retrieveConfig = {
                    retrieveConfigCount.incrementAndGet()
                    Config(size = 42)
                },
                create = {
                    createCount.incrementAndGet()
                    Singleton(content = originalContent)
                },
                destroy = {
                    destroyCount.incrementAndGet()
                }
            )

            existingRef.required().content shouldBe ("test")

            val newRef = existingRef.singleton(
                retrieveConfig = {
                    retrieveConfigCount.incrementAndGet()
                    Config(size = 21)
                },
                create = {
                    createCount.incrementAndGet()
                    Singleton(content = "other")
                },
                destroy = {
                    destroyCount.incrementAndGet()
                }
            )

            newRef shouldBe (existingRef)

            newRef.required().content shouldBe (originalContent)

            retrieveConfigCount.get() shouldBe (2)
            createCount.get() shouldBe (1)
            destroyCount.get() shouldBe (0)
        }
    }

    "An Empty Reference" should {
        "not provide instances as objects" {
            val ref = Reference.Empty<String>()

            ref.isEmpty() shouldBe (true)
            ref.isNotEmpty() shouldBe (false)

            ref.provided() shouldBe (null)
            ref.provided { it.uppercase() } shouldBe (null)

            shouldThrow<IllegalArgumentException> { ref.required() }
                .message shouldBe ("Cannot require an empty reference")

            shouldThrow<IllegalArgumentException> { ref.required<Any> { fail("Expected failure but none was encountered") } }
                .message shouldBe ("Cannot require an empty reference")
        }

        "not map instances" {
            val ref = Reference.Empty<String>()

            ref.isEmpty() shouldBe (true)
            ref.isNotEmpty() shouldBe (false)

            ref.map { it.toInt() }.isEmpty() shouldBe (true)
        }

        "support creating new singleton instances" {
            val retrieveConfigCount = AtomicInteger(0)
            val createCount = AtomicInteger(0)
            val destroyCount = AtomicInteger(0)

            val expectedContent = "test"

            val ref = Reference.Empty<Singleton>().singleton(
                retrieveConfig = {
                    retrieveConfigCount.incrementAndGet()
                    Config(size = 42)
                },
                create = {
                    createCount.incrementAndGet()
                    Singleton(content = expectedContent)
                },
                destroy = {
                    destroyCount.incrementAndGet()
                }
            )

            ref.isEmpty() shouldBe (true)
            ref.isNotEmpty() shouldBe (false)

            ref.required().content shouldBe(expectedContent)
            ref.required().content shouldBe(expectedContent)
            ref.required().content shouldBe(expectedContent)

            retrieveConfigCount.get() shouldBe (3)
            createCount.get() shouldBe (1)
            destroyCount.get() shouldBe (0)

            ref.isEmpty() shouldBe (false)
            ref.isNotEmpty() shouldBe (true)
        }
    }
})
