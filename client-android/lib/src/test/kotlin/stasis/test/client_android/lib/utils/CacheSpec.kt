package stasis.test.client_android.lib.utils

import io.kotest.assertions.fail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import okio.utf8Size
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

@Suppress("LargeClass")
class CacheSpec : WordSpec({
    val key = "test-key"
    val value = "test-initial-value"

    "Cache OperationStatistics" should {
        "support updating" {
            val empty = Cache.OperationStatistics.empty()

            empty.bytesProcessed shouldBe (0)
            empty.minDuration shouldBe (Long.MAX_VALUE)
            empty.maxDuration shouldBe (Long.MIN_VALUE)
            empty.operations shouldBe (0)

            val firstUpdate = empty.updateWith(amount = 50, duration = 100)

            firstUpdate.bytesProcessed shouldBe (50)
            firstUpdate.minDuration shouldBe (100)
            firstUpdate.maxDuration shouldBe (100)
            firstUpdate.operations shouldBe (1)

            val secondUpdate = firstUpdate.updateWith(amount = 30, duration = 150)

            secondUpdate.bytesProcessed shouldBe (80)
            secondUpdate.minDuration shouldBe (100)
            secondUpdate.maxDuration shouldBe (150)
            secondUpdate.operations shouldBe (2)

            val thirdUpdate = secondUpdate.updateWith(amount = 30, duration = 50)

            thirdUpdate.bytesProcessed shouldBe (110)
            thirdUpdate.minDuration shouldBe (50)
            thirdUpdate.maxDuration shouldBe (150)
            thirdUpdate.operations shouldBe (3)
        }
    }

    "A Map Cache" should {
        "support caching data" {
            val loadedValues = AtomicInteger(0)

            val load: suspend (String) -> String = {
                loadedValues.incrementAndGet()
                value
            }

            val cache = Cache.Map<String, String>()

            cache.readStatistics.bytesProcessed shouldBe (0)
            cache.writeStatistics.bytesProcessed shouldBe (0)

            loadedValues.get() shouldBe (0)
            cache.get(key) shouldBe (null)

            cache.getOrLoad(key, load) shouldBe (value)
            cache.getOrLoad(key, load) shouldBe (value)
            cache.getOrLoad(key, load) shouldBe (value)

            loadedValues.get() shouldBe (1)

            cache.readStatistics.bytesProcessed shouldBe (0) // tracking data size is not supported
            cache.writeStatistics.bytesProcessed shouldBe (0) // tracking data size is not supported
        }

        "support explicitly adding data (individual)" {
            val cache = Cache.Map<String, String>()

            cache.get(key) shouldBe (null)
            cache.put(key, value)
            cache.get(key) shouldBe (value)
        }

        "support explicitly adding data (bulk)" {
            val cache = Cache.Map<String, String>()

            val key1 = "test-key-1"
            val key2 = "test-key-2"
            val key3 = "test-key-3"

            cache.get(key1) shouldBe (null)
            cache.get(key2) shouldBe (null)
            cache.get(key3) shouldBe (null)
            cache.put(mapOf(key1 to value, key2 to value, key3 to value))
            cache.get(key1) shouldBe (value)
            cache.get(key2) shouldBe (value)
            cache.get(key3) shouldBe (value)
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

        "support retrieving all cached data" {
            val cache = Cache.Map<String, String>()

            cache.put("k1", "v1")
            cache.put("k2", "v2")
            cache.put("k3", "v3")
            cache.put("k4", "v4")
            cache.put("k5", "v5")

            val expected = mapOf(
                "k1" to "v1",
                "k2" to "v2",
                "k3" to "v3",
                "k4" to "v4",
                "k5" to "v5"
            )

            cache.all() shouldBe (expected)
        }

        "support clearing all cached data" {
            val cache = Cache.Map<String, String>()

            cache.put("k1", "v1")
            cache.put("k2", "v2")
            cache.put("k3", "v3")

            cache.all().size shouldBe (3)

            cache.clear()

            cache.all() shouldBe (emptyMap())
        }
    }

    "A File Cache" should {
        val setup = FileSystemSetup.Unix

        val serdes = object : Cache.File.Serdes<String, String> {
            override fun serializeKey(key: String): String = key
            override fun deserializeKey(key: String): String = key
            override fun serializeValue(value: String): ByteArray = value.toByteArray()
            override fun deserializeValue(value: ByteArray): Try<String> = Try.Success(String(value))
        }

        "support caching data" {
            val (filesystem, _) = createMockFileSystem(setup)

            val target = filesystem.getPath("/cache")

            val loadedValues = AtomicInteger(0)

            val load: suspend (String) -> String = {
                loadedValues.incrementAndGet()
                value
            }

            val cache = Cache.File(target, serdes)

            cache.readStatistics.bytesProcessed shouldBe (0)
            cache.writeStatistics.bytesProcessed shouldBe (0)

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

            cache.readStatistics.bytesProcessed shouldBe (0) // read from memory
            cache.writeStatistics.bytesProcessed shouldBe (value.utf8Size())
        }

        "support reading cached data from storage" {
            val (filesystem, _) = createMockFileSystem(setup)

            val target = filesystem.getPath("/cache")

            val loadedValues = AtomicInteger(0)

            val primaryCache = Cache.File(target, serdes)
            val secondaryCache = Cache.File(target, serdes)

            primaryCache.readStatistics.bytesProcessed shouldBe (0)
            primaryCache.writeStatistics.bytesProcessed shouldBe (0)

            secondaryCache.readStatistics.bytesProcessed shouldBe (0)
            secondaryCache.writeStatistics.bytesProcessed shouldBe (0)

            loadedValues.get() shouldBe (0)
            primaryCache.get(key) shouldBe (null)
            secondaryCache.get(key) shouldBe (null)

            primaryCache.put(key, value)
            secondaryCache.get(key) shouldBe (value)

            primaryCache.readStatistics.bytesProcessed shouldBe (0) // no data read
            primaryCache.writeStatistics.bytesProcessed shouldBe (value.utf8Size())

            secondaryCache.readStatistics.bytesProcessed shouldBe (value.utf8Size())
            secondaryCache.writeStatistics.bytesProcessed shouldBe (0) // no data written
        }

        "support explicitly adding data (individual)" {
            val (filesystem, _) = createMockFileSystem(setup)

            val target = filesystem.getPath("/cache")

            val cache = Cache.File(target, serdes)

            cache.readStatistics.bytesProcessed shouldBe (0)
            cache.writeStatistics.bytesProcessed shouldBe (0)

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

            cache.readStatistics.bytesProcessed shouldBe (0) // read from memory
            cache.writeStatistics.bytesProcessed shouldBe (value.utf8Size())
        }

        "support explicitly adding data (bulk)" {
            val (filesystem, _) = createMockFileSystem(setup)

            val target = filesystem.getPath("/cache")

            val cache = Cache.File(target, serdes)

            cache.readStatistics.bytesProcessed shouldBe (0)
            cache.writeStatistics.bytesProcessed shouldBe (0)

            val key1 = "test-key-1"
            val key2 = "test-key-2"
            val key3 = "test-key-3"

            cache.get(key1) shouldBe (null)
            cache.get(key2) shouldBe (null)
            cache.get(key3) shouldBe (null)
            cache.put(mapOf(key1 to value, key2 to value, key3 to value))
            cache.get(key1) shouldBe (value)
            cache.get(key2) shouldBe (value)
            cache.get(key3) shouldBe (value)

            val cachedPaths = target.files().filter { it.fileName.toString().startsWith("test-key") }
            cachedPaths.size shouldBe (3)

            cachedPaths.forEach { cachedPath ->
                val content = cachedPath.content()
                val deserialized = serdes.deserializeValue(content.toByteArray())
                deserialized shouldBe (Try.Success(value))
            }

            cache.readStatistics.bytesProcessed shouldBe (0) // read from memory
            cache.writeStatistics.bytesProcessed shouldBe (3 * value.utf8Size())
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

            cache.readStatistics.bytesProcessed shouldBe (0)
            cache.writeStatistics.bytesProcessed shouldBe (0)

            loadedValues.get() shouldBe (0)
            cache.get(key) shouldBe (null)

            cache.getOrLoad(key, load) shouldBe (value)

            cache.remove(key)
            cache.get(key) shouldBe (null)

            cache.getOrLoad(key, load) shouldBe (value)
            cache.getOrLoad(key, load) shouldBe (value)

            loadedValues.get() shouldBe (2)

            cache.readStatistics.bytesProcessed shouldBe (0) // read from memory
            cache.writeStatistics.bytesProcessed shouldBe (2 * value.utf8Size())
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

            cache.readStatistics.bytesProcessed shouldBe (0)
            cache.writeStatistics.bytesProcessed shouldBe (0)

            loadedValues.get() shouldBe (0)
            cache.get(key) shouldBe (null)

            val e = shouldThrow<RuntimeException> {
                cache.getOrLoad(key, load)
            }

            e.message shouldBe ("Test failure")

            loadedValues.get() shouldBe (1)
            cache.get(key) shouldBe (null)

            cache.readStatistics.bytesProcessed shouldBe (0)
            cache.writeStatistics.bytesProcessed shouldBe (0)
        }

        "support retrieving all cached data" {
            val (filesystem, _) = createMockFileSystem(setup)

            val target = filesystem.getPath("/cache")

            val cache = Cache.File(target, serdes)

            cache.readStatistics.bytesProcessed shouldBe (0)
            cache.writeStatistics.bytesProcessed shouldBe (0)

            cache.put("k1", "v1")
            cache.put("k2", "v2")
            cache.put("k3", "v3")
            cache.put("k4", "v4")
            cache.put("k5", "v5")

            val expected = mapOf(
                "k1" to "v1",
                "k2" to "v2",
                "k3" to "v3",
                "k4" to "v4",
                "k5" to "v5"
            )

            cache.all() shouldBe (expected)

            cache.readStatistics.bytesProcessed shouldBe (0) // read from memory
            cache.writeStatistics.bytesProcessed shouldBe (10)
        }

        "support clearing all cached data" {
            val (filesystem, _) = createMockFileSystem(setup)

            val target = filesystem.getPath("/cache")

            val cache = Cache.File(target, serdes)

            cache.readStatistics.bytesProcessed shouldBe (0)
            cache.writeStatistics.bytesProcessed shouldBe (0)

            cache.put("k1", "v1")
            cache.put("k2", "v2")
            cache.put("k3", "v3")

            cache.all().size shouldBe (3)

            cache.clear()

            cache.all() shouldBe (emptyMap())

            cache.readStatistics.bytesProcessed shouldBe (0) // read from memory
            cache.writeStatistics.bytesProcessed shouldBe (6)
        }

        "handle failures when retrieving all cache entries" {
            val (filesystem, _) = createMockFileSystem(setup)

            val target = filesystem.getPath("/cache")

            val cache = Cache.File(target, serdes)

            cache.all().size shouldBe (0)
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

            cache.readStatistics.bytesProcessed shouldBe (0)
            cache.writeStatistics.bytesProcessed shouldBe (0)

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

            cache.readStatistics.bytesProcessed shouldBe (0) // underlying cache doesn't track bytes
            cache.writeStatistics.bytesProcessed shouldBe (0) // underlying cache doesn't track bytes
        }

        "support explicitly adding data (individual)" {
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

        "support explicitly adding data (bulk)" {
            val expiredValues = AtomicInteger(0)

            val expiration = Duration.ofMillis(250)

            val cache = Cache.Expiring<String, String>(
                underlying = Cache.Map(),
                expiration = expiration,
                scope = CoroutineScope(Dispatchers.IO)
            )

            cache.registerOnEntryExpiredListener { expiredValues.incrementAndGet() }

            expiredValues.get() shouldBe (0)

            val key1 = "test-key-1"
            val key2 = "test-key-2"
            val key3 = "test-key-3"

            cache.get(key1) shouldBe (null)
            cache.get(key2) shouldBe (null)
            cache.get(key3) shouldBe (null)

            cache.put(mapOf(key1 to value, key2 to value, key3 to value))

            eventually {
                cache.get(key1) shouldBe (null)
                cache.get(key2) shouldBe (null)
                cache.get(key3) shouldBe (null)
                expiredValues.get() shouldBe (3)
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

        "support retrieving all cached data" {
            val expiration = Duration.ofMillis(250)

            val cache = Cache.Expiring<String, String>(
                underlying = Cache.Map(),
                expiration = expiration,
                scope = CoroutineScope(Dispatchers.IO)
            )

            cache.put("k1", "v1")
            cache.put("k2", "v2")
            cache.put("k3", "v3")
            cache.put("k4", "v4")
            cache.put("k5", "v5")

            val expected = mapOf(
                "k1" to "v1",
                "k2" to "v2",
                "k3" to "v3",
                "k4" to "v4",
                "k5" to "v5"
            )

            cache.all() shouldBe (expected)
        }

        "support clearing all cached data" {
            val expiration = Duration.ofMillis(250)

            val cache = Cache.Expiring<String, String>(
                underlying = Cache.Map(),
                expiration = expiration,
                scope = CoroutineScope(Dispatchers.IO)
            )

            cache.put("k1", "v1")
            cache.put("k2", "v2")
            cache.put("k3", "v3")

            cache.all().size shouldBe (3)

            cache.clear()

            cache.all() shouldBe (emptyMap())
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

            cache.readStatistics.bytesProcessed shouldBe (0)
            cache.writeStatistics.bytesProcessed shouldBe (0)

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

            cache.readStatistics.bytesProcessed shouldBe (0) // underlying cache doesn't track bytes
            cache.writeStatistics.bytesProcessed shouldBe (0) // underlying cache doesn't track bytes
        }

        "support explicitly adding data (individual)" {
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

        "support explicitly adding data (bulk)" {
            val refreshedValues = AtomicInteger(0)

            val interval = Duration.ofMillis(100)

            val cache = Cache.Refreshing<String, String>(
                underlying = Cache.Map(),
                interval = interval,
                scope = CoroutineScope(Dispatchers.IO)
            )

            cache.registerOnEntryRefreshedListener { _, _ -> refreshedValues.incrementAndGet() }

            refreshedValues.get() shouldBe (0)

            val key1 = "test-key-1"
            val key2 = "test-key-2"
            val key3 = "test-key-3"

            cache.get(key1) shouldBe (null)
            cache.get(key2) shouldBe (null)
            cache.get(key3) shouldBe (null)

            cache.put(mapOf(key1 to value, key2 to value, key3 to value))

            cache.get(key1) shouldBe (value)
            cache.get(key2) shouldBe (value)
            cache.get(key3) shouldBe (value)

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

        "support retrieving all cached data" {
            val interval = Duration.ofMillis(100)

            val cache = Cache.Refreshing<String, String>(
                underlying = Cache.Map(),
                interval = interval,
                scope = CoroutineScope(Dispatchers.IO)
            )

            cache.put("k1", "v1")
            cache.put("k2", "v2")
            cache.put("k3", "v3")
            cache.put("k4", "v4")
            cache.put("k5", "v5")

            val expected = mapOf(
                "k1" to "v1",
                "k2" to "v2",
                "k3" to "v3",
                "k4" to "v4",
                "k5" to "v5"
            )

            cache.all() shouldBe (expected)
        }

        "support clearing all cached data" {
            val interval = Duration.ofMillis(100)

            val cache = Cache.Refreshing<String, String>(
                underlying = Cache.Map(),
                interval = interval,
                scope = CoroutineScope(Dispatchers.IO)
            )

            cache.put("k1", "v1")
            cache.put("k2", "v2")
            cache.put("k3", "v3")

            cache.all().size shouldBe (3)

            cache.clear()

            cache.all() shouldBe (emptyMap())
        }
    }

    "A Tracking Cache" should {
        "support caching data" {
            val loadedValues = AtomicInteger(0)

            val load: suspend (String) -> String = {
                loadedValues.incrementAndGet()
                value
            }

            val underlying = Cache.Map<String, String>()
            val cache = Cache.Tracking(underlying)

            cache.readStatistics.bytesProcessed shouldBe (0)
            cache.writeStatistics.bytesProcessed shouldBe (0)

            cache.hits shouldBe (0)
            cache.misses shouldBe (0)

            loadedValues.get() shouldBe (0)
            cache.get(key) shouldBe (null) // miss

            cache.hits shouldBe (0)
            cache.misses shouldBe (1)

            cache.getOrLoad(key, load) shouldBe (value) // miss + load
            cache.getOrLoad(key, load) shouldBe (value) // hit
            cache.getOrLoad(key, load) shouldBe (value) // hit

            cache.hits shouldBe (2)
            cache.misses shouldBe (2)

            loadedValues.get() shouldBe (1)

            cache.readStatistics.bytesProcessed shouldBe (0) // underlying cache doesn't track bytes
            cache.writeStatistics.bytesProcessed shouldBe (0) // underlying cache doesn't track bytes
        }

        "support explicitly adding data (individual)" {
            val underlying = Cache.Map<String, String>()
            val cache = Cache.Tracking(underlying)

            cache.hits shouldBe (0)
            cache.misses shouldBe (0)

            cache.get(key) shouldBe (null) // miss

            cache.hits shouldBe (0)
            cache.misses shouldBe (1)

            cache.put(key, value)
            cache.get(key) shouldBe (value) // hit

            cache.hits shouldBe (1)
            cache.misses shouldBe (1)
        }

        "support explicitly adding data (bulk)" {
            val underlying = Cache.Map<String, String>()
            val cache = Cache.Tracking(underlying)

            val key1 = "test-key-1"
            val key2 = "test-key-2"
            val key3 = "test-key-3"

            cache.hits shouldBe (0)
            cache.misses shouldBe (0)

            cache.get(key1) shouldBe (null) // miss
            cache.get(key2) shouldBe (null) // miss
            cache.get(key3) shouldBe (null) // miss

            cache.hits shouldBe (0)
            cache.misses shouldBe (3)

            cache.put(mapOf(key1 to value, key2 to value, key3 to value))
            cache.get(key1) shouldBe (value) // hit
            cache.get(key2) shouldBe (value) // hit
            cache.get(key3) shouldBe (value) // hit

            cache.hits shouldBe (3)
            cache.misses shouldBe (3)
        }

        "support removing data" {
            val loadedValues = AtomicInteger(0)

            val load: suspend (String) -> String = {
                loadedValues.incrementAndGet()
                value
            }

            val underlying = Cache.Map<String, String>()
            val cache = Cache.Tracking(underlying)

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

            val underlying = Cache.Map<String, String>()
            val cache = Cache.Tracking(underlying)

            loadedValues.get() shouldBe (0)
            cache.get(key) shouldBe (null)

            val e = shouldThrow<RuntimeException> {
                cache.getOrLoad(key, load)
            }

            e.message shouldBe ("Test failure")

            loadedValues.get() shouldBe (1)
            cache.get(key) shouldBe (null)
        }

        "support retrieving all cached data" {
            val underlying = Cache.Map<String, String>()
            val cache = Cache.Tracking(underlying)

            cache.put("k1", "v1")
            cache.put("k2", "v2")
            cache.put("k3", "v3")
            cache.put("k4", "v4")
            cache.put("k5", "v5")

            val expected = mapOf(
                "k1" to "v1",
                "k2" to "v2",
                "k3" to "v3",
                "k4" to "v4",
                "k5" to "v5"
            )

            cache.all() shouldBe (expected)
        }

        "support clearing all cached data" {
            val underlying = Cache.Map<String, String>()
            val cache = Cache.Tracking(underlying)

            cache.put("k1", "v1")
            cache.put("k2", "v2")
            cache.put("k3", "v3")

            cache.all().size shouldBe (3)

            cache.clear()

            cache.all() shouldBe (emptyMap())
        }
    }
})
