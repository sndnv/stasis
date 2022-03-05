package stasis.test.client_android.lib.utils

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import stasis.client_android.lib.utils.Cache
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
