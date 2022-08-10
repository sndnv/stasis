package stasis.test.client_android.lib.utils

import io.kotest.assertions.fail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import stasis.client_android.lib.utils.Cache
import stasis.client_android.lib.utils.Try
import stasis.test.client_android.lib.ResourceHelpers.FileSystemSetup
import stasis.test.client_android.lib.ResourceHelpers.content
import stasis.test.client_android.lib.ResourceHelpers.createMockFileSystem
import stasis.test.client_android.lib.ResourceHelpers.files
import stasis.test.client_android.lib.awaitAndThen
import stasis.test.client_android.lib.collectPeriodically
import stasis.test.client_android.lib.eventually
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class CacheSpec : WordSpec({
    val key = "test-key"
    val value = "test-initial-value"

    "A Map Cache" should {
        "support caching data" {
            val loadedValues = AtomicInteger(0)

            val load: suspend (String) -> String = {
                loadedValues.incrementAndGet()
                value
            }

            val cache = Cache.Map<String, String>()

            loadedValues.get() shouldBe (0)
            cache.get(key) shouldBe (null)

            cache.getOrLoad(key, load) shouldBe (value)
            cache.getOrLoad(key, load) shouldBe (value)
            cache.getOrLoad(key, load) shouldBe (value)

            loadedValues.get() shouldBe (1)
        }

        "support explicitly adding data" {
            val cache = Cache.Map<String, String>()

            cache.get(key) shouldBe (null)
            cache.put(key, value)
            cache.get(key) shouldBe (value)
        }

        "support removing data" {
            val loadedValues = AtomicInteger(0)

            val load: suspend (String) -> String = {
                loadedValues.incrementAndGet()
                value
            }

            val cache = Cache.Map<String, String>()

            loadedValues.get() shouldBe (0)
            cache.get(key) shouldBe (null)

            cache.getOrLoad(key, load) shouldBe (value)

            cache.remove(key)
            cache.get(key) shouldBe (null)

            cache.getOrLoad(key, load) shouldBe (value)
            cache.getOrLoad(key, load) shouldBe (value)

            loadedValues.get() shouldBe (2)
        }

        "not update the cache if the load operation fails" {
            val loadedValues = AtomicInteger(0)

            val load: suspend (String) -> String = {
                loadedValues.incrementAndGet()
                throw RuntimeException("Test failure")
            }

            val cache = Cache.Map<String, String>()

            loadedValues.get() shouldBe (0)
            cache.get(key) shouldBe (null)

            val e = shouldThrow<RuntimeException> {
                cache.getOrLoad(key, load)
            }

            e.message shouldBe ("Test failure")

            loadedValues.get() shouldBe (1)
            cache.get(key) shouldBe (null)
        }
    }

    "A File Cache" should {
        val setup = FileSystemSetup.Unix

        val serdes = object : Cache.File.Serdes<String, String> {
            override fun serializeKey(key: String): String = key
            override fun serializeValue(value: String): ByteArray = value.toByteArray()
            override fun deserializeValue(value: ByteArray): Try<String> = Try.Success(String(value))
        }

        "support caching data"  {
            val (filesystem, _) = createMockFileSystem(setup)

            val target = filesystem.getPath("/cache")

            val loadedValues = AtomicInteger(0)

            val load: suspend (String) -> String = {
                loadedValues.incrementAndGet()
                value
            }

            val cache = Cache.File(target, serdes)

            loadedValues.get() shouldBe (0)
            cache.get(key) shouldBe (null)

            cache.getOrLoad(key, load) shouldBe (value)
            cache.getOrLoad(key, load) shouldBe (value)
            cache.getOrLoad(key, load) shouldBe (value)

            loadedValues.get() shouldBe (1)

            val cachedPath = when (val file = target.files().firstOrNull()) {
                null -> fail("Expected at least one file but none were found")
                else -> file
            }

            val content = cachedPath.content()
            val deserialized = serdes.deserializeValue(content.toByteArray())
            deserialized shouldBe (Try.Success(value))
        }

        "support explicitly adding data" {
            val (filesystem, _) = createMockFileSystem(setup)

            val target = filesystem.getPath("/cache")

            val cache = Cache.File(target, serdes)

            cache.get(key) shouldBe (null)
            cache.put(key, value)
            cache.get(key) shouldBe (value)

            val cachedPath = when (val file = target.files().firstOrNull()) {
                null -> fail("Expected at least one file but none were found")
                else -> file
            }

            val content = cachedPath.content()
            val deserialized = serdes.deserializeValue(content.toByteArray())
            deserialized shouldBe (Try.Success(value))
        }

        "support removing data" {
            val (filesystem, _) = createMockFileSystem(setup)

            val target = filesystem.getPath("/cache")

            val loadedValues = AtomicInteger(0)

            val load: suspend (String) -> String = {
                loadedValues.incrementAndGet()
                value
            }

            val cache = Cache.File(target, serdes)

            loadedValues.get() shouldBe (0)
            cache.get(key) shouldBe (null)

            cache.getOrLoad(key, load) shouldBe (value)

            cache.remove(key)
            cache.get(key) shouldBe (null)

            cache.getOrLoad(key, load) shouldBe (value)
            cache.getOrLoad(key, load) shouldBe (value)

            loadedValues.get() shouldBe (2)
        }

        "not update the cache if the load operation fails" {
            val (filesystem, _) = createMockFileSystem(setup)

            val target = filesystem.getPath("/cache")

            val loadedValues = AtomicInteger(0)

            val load: suspend (String) -> String = {
                loadedValues.incrementAndGet()
                throw RuntimeException("Test failure")
            }

            val cache = Cache.File(target, serdes)

            loadedValues.get() shouldBe (0)
            cache.get(key) shouldBe (null)

            val e = shouldThrow<RuntimeException> {
                cache.getOrLoad(key, load)
            }

            e.message shouldBe ("Test failure")

            loadedValues.get() shouldBe (1)
            cache.get(key) shouldBe (null)
        }
    }

    "An Expiring Cache" should {
        "support caching and expiring data" {
            val loadedValues = AtomicInteger(0)
            val expiredValues = AtomicInteger(0)

            val expiration = Duration.ofMillis(250)

            val load: suspend (String) -> String = {
                loadedValues.incrementAndGet()
                value
            }

            val cache = Cache.Expiring<String, String>(
                underlying = Cache.Map(),
                expiration = expiration,
                scope = CoroutineScope(Dispatchers.IO)
            )

            cache.registerOnEntryExpiredListener { expiredValues.incrementAndGet() }

            loadedValues.get() shouldBe (0)
            expiredValues.get() shouldBe (0)
            cache.get(key) shouldBe (null)

            cache.getOrLoad(key, load) shouldBe (value)
            cache.getOrLoad(key, load) shouldBe (value)
            cache.getOrLoad(key, load) shouldBe (value)

            loadedValues.get() shouldBe (1)

            eventually {
                cache.get(key) shouldBe (null)
                expiredValues.get() shouldBe (1)
            }

            eventually {
                cache.getOrLoad(key, load) shouldBe (value)
                loadedValues.get() shouldBe (2)
            }
        }

        "support explicitly adding data" {
            val expiredValues = AtomicInteger(0)

            val expiration = Duration.ofMillis(250)

            val cache = Cache.Expiring<String, String>(
                underlying = Cache.Map(),
                expiration = expiration,
                scope = CoroutineScope(Dispatchers.IO)
            )

            cache.registerOnEntryExpiredListener { expiredValues.incrementAndGet() }

            expiredValues.get() shouldBe (0)
            cache.get(key) shouldBe (null)

            cache.put(key, value)

            eventually {
                cache.get(key) shouldBe (null)
                expiredValues.get() shouldBe (1)
            }
        }

        "support removing data" {
            val loadedValues = AtomicInteger(0)

            val expiration = Duration.ofMillis(250)

            val load: suspend (String) -> String = {
                loadedValues.incrementAndGet()
                value
            }

            val cache = Cache.Expiring<String, String>(
                underlying = Cache.Map(),
                expiration = expiration,
                scope = testScope
            )

            loadedValues.get() shouldBe (0)
            cache.get(key) shouldBe (null)

            cache.getOrLoad(key, load) shouldBe (value)

            cache.remove(key)
            cache.get(key) shouldBe (null)
        }

        "support registering and unregistering expiration event listeners" {
            val listenerCalls = AtomicInteger(0)

            val cache = Cache.Expiring<String, String>(
                underlying = Cache.Map(),
                expiration = Duration.ofMillis(50),
                scope = CoroutineScope(Dispatchers.IO)
            )

            val listener: (String) -> Unit = { listenerCalls.incrementAndGet() }

            cache.registerOnEntryExpiredListener(listener)

            cache.getOrLoad(key, load = { value }) shouldBe (value)

            eventually {
                cache.get(key) shouldBe (null)
                listenerCalls.get() shouldBe (1)
            }

            cache.getOrLoad(key, load = { value }) shouldBe (value)

            eventually {
                cache.get(key) shouldBe (null)
                listenerCalls.get() shouldBe (2)
            }

            cache.unregisterOnEntryExpiredListener(listener)

            cache.getOrLoad(key, load = { value }) shouldBe (value)

            eventually {
                cache.get(key) shouldBe (null)
                listenerCalls.get() shouldBe (2)
            }
        }
    }

    "A Refreshing Cache" should {
        "support caching and refreshing data" {
            val loadedValues = AtomicInteger(0)
            val refreshedValues = AtomicInteger(0)

            val interval = Duration.ofMillis(100)

            val load: suspend (String) -> String = {
                loadedValues.incrementAndGet()
                value
            }

            val cache = Cache.Refreshing<String, String>(
                underlying = Cache.Map(),
                interval = interval,
                scope = CoroutineScope(Dispatchers.IO)
            )

            cache.registerOnEntryRefreshedListener { _, _ -> refreshedValues.incrementAndGet() }

            loadedValues.get() shouldBe (0)
            refreshedValues.get() shouldBe (0)
            cache.get(key) shouldBe (null)

            cache.getOrLoad(key, load) shouldBe (value)
            cache.getOrLoad(key, load) shouldBe (value)
            cache.getOrLoad(key, load) shouldBe (value)

            loadedValues.get() shouldBe (1)

            eventually {
                refreshedValues.get() shouldBe (3)
                loadedValues.get() shouldBe (4)
            }
        }

        "support explicitly adding data" {
            val refreshedValues = AtomicInteger(0)

            val interval = Duration.ofMillis(100)

            val cache = Cache.Refreshing<String, String>(
                underlying = Cache.Map(),
                interval = interval,
                scope = CoroutineScope(Dispatchers.IO)
            )

            cache.registerOnEntryRefreshedListener { _, _ -> refreshedValues.incrementAndGet() }

            refreshedValues.get() shouldBe (0)
            cache.get(key) shouldBe (null)

            cache.put(key, value)

            cache.get(key) shouldBe (value)

            awaitAndThen {
                refreshedValues.get() shouldBe (0)
            }
        }

        "support keeping stale entries until a refresh is successful" {
            val loadedValues = AtomicInteger(0)
            val failedRefreshes = AtomicInteger(0)
            val successfulRefreshes = AtomicInteger(0)

            val interval = Duration.ofMillis(100)
            val failAfterValues = 3

            val load: suspend (String) -> String = {
                val loaded = loadedValues.getAndIncrement()

                if (loaded >= failAfterValues) {
                    failedRefreshes.incrementAndGet()
                    throw RuntimeException("Test failure")
                } else {
                    value
                }
            }

            val cache = Cache.Refreshing<String, String>(
                underlying = Cache.Map(),
                interval = interval,
                scope = CoroutineScope(Dispatchers.IO)
            )

            cache.registerOnEntryRefreshedListener { _, _ -> successfulRefreshes.incrementAndGet() }

            loadedValues.get() shouldBe (0)
            failedRefreshes.get() shouldBe (0)
            successfulRefreshes.get() shouldBe (0)
            cache.get(key) shouldBe (null)

            cache.getOrLoad(key, load) shouldBe (value)
            cache.getOrLoad(key, load) shouldBe (value)
            cache.getOrLoad(key, load) shouldBe (value)

            loadedValues.get() shouldBe (1)

            val collectionDuration = Duration.ofSeconds(1)
            val collectionInterval = Duration.ofMillis(100)
            val expectedValues = collectionDuration.dividedBy(collectionInterval).toInt()

            val collected = collectPeriodically {
                cache.getOrLoad(key, load)
            }

            collected.size shouldBe (expectedValues)

            collected.any { it.isFailure } shouldBe (false)

            collected.map { it.getOrElse { "invalid" } }.forEach {
                it shouldBe (value)
            }

            successfulRefreshes.get() shouldBe (failAfterValues - 1)
            loadedValues.get() shouldBeGreaterThanOrEqual (expectedValues)
            failedRefreshes.get() shouldBeGreaterThanOrEqual (expectedValues - failAfterValues)
        }

        "support removing data" {
            val loadedValues = AtomicInteger(0)

            val interval = Duration.ofMillis(100)

            val load: suspend (String) -> String = {
                loadedValues.incrementAndGet()
                value
            }

            val cache = Cache.Refreshing<String, String>(
                underlying = Cache.Map(),
                interval = interval,
                scope = CoroutineScope(Dispatchers.IO)
            )

            loadedValues.get() shouldBe (0)
            cache.get(key) shouldBe (null)

            cache.getOrLoad(key, load) shouldBe (value)

            cache.remove(key)
            cache.get(key) shouldBe (null)
        }

        "support registering and unregistering refresh event listeners" {
            val listenerCalls = AtomicInteger(0)

            val interval = Duration.ofMillis(50)

            val cache = Cache.Refreshing<String, String>(
                underlying = Cache.Map(),
                interval = interval,
                scope = CoroutineScope(Dispatchers.IO)
            )

            val listener: (String, String?) -> Unit = { _, _ -> listenerCalls.incrementAndGet() }

            cache.registerOnEntryRefreshedListener(listener)

            cache.getOrLoad(key, load = { value }) shouldBe (value)

            eventually {
                listenerCalls.get() shouldBeGreaterThanOrEqual (5)
            }

            val actualCalls = listenerCalls.get()

            cache.unregisterOnEntryRefreshedListener(listener)

            delay(interval.toMillis() * 3)

            listenerCalls.get() shouldBe (actualCalls)
        }
    }
})
