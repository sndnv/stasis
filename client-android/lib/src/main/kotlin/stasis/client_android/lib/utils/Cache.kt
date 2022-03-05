package stasis.client_android.lib.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

interface Cache<K : Any, V> {

    /**
     * Retrieves the cached value associated with [key], if any.
     *
     * @param key the key associated with the needed value
     * @return the cached value or `null` if none was found
     */
    suspend fun get(key: K): V?


    /**
     * Retrieves the cached value associated with [key] or attempts to load it.
     *
     * @param key the key associated with the needed value
     * @param load function used for loading the value if it is not already in the cache
     * @return the cached or loaded value
     */
    suspend fun getOrLoad(key: K, load: suspend (K) -> V?): V?

    /**
     * Removes the cached value associated with [key], if any.
     *
     * @param key the key associated with value to be removed
     */
    suspend fun remove(key: K)

    /**
     * Simple in-memory cache backed by [ConcurrentHashMap].
     */
    class Map<K : Any, V> : Cache<K, V> {
        private val map: ConcurrentHashMap<K, V> = ConcurrentHashMap()

        override suspend fun get(key: K): V? = map[key]

        override suspend fun getOrLoad(key: K, load: suspend (K) -> V?): V? =
            get(key) ?: load(key)?.also { map[key] = it }

        override suspend fun remove(key: K) {
            map.remove(key)
        }
    }

    /**
     * Time-based expiration cache.
     *
     * Newly loaded values are scheduled to expire after the specified amount of time has passed.
     *
     * @param underlying cache that handles the actual data storage
     * @param expiration amount of time to keep values in the cache
     * @param scope coroutine scope for executing expiration tasks
     */
    class Expiring<K : Any, V>(
        private val underlying: Cache<K, V>,
        private val expiration: Duration,
        private val scope: CoroutineScope,
    ) : Cache<K, V> {
        private val listeners: ConcurrentLinkedQueue<(K) -> Unit> = ConcurrentLinkedQueue()

        override suspend fun get(key: K): V? =
            underlying.get(key)

        override suspend fun getOrLoad(key: K, load: suspend (K) -> V?): V? =
            underlying.getOrLoad(key, load = { k -> load(k)?.also { expire(k) } })

        override suspend fun remove(key: K) =
            underlying.remove(key)

        fun registerOnEntryExpiredListener(listener: (K) -> Unit) {
            listeners.add(listener)
        }

        fun unregisterOnEntryExpiredListener(listener: (K) -> Unit) {
            listeners.remove(listener)
        }

        private fun expire(key: K) {
            try {
                scope.launch {
                    delay(timeMillis = expiration.toMillis())
                    remove(key)
                    listeners.forEach { it(key) }
                }
            } catch (_: CancellationException) {
                // do nothing
            }
        }
    }

    /**
     * Time-based refreshing cache.
     *
     * After the specified amount of time has passed, values are reloaded using the `load` function
     * initially associated with a key. This means that using different `load` functions for subsequent
     * calls to [getOrLoad] will not be taken into account during refreshes.
     *
     * Care needs to be taken to avoid multiple `load` functions as this will lead to inconsistencies
     * when the [underlying] cache loads values and this cache refreshes them. The proper way to reset
     * a `load` function is to [remove] the entry and re-add it with the new function.
     *
     * @param underlying cache that handles the actual data storage
     * @param interval amount of time to wait before refreshing values
     * @param scope coroutine scope for executing refresh tasks
     */
    class Refreshing<K : Any, V>(
        private val underlying: Cache<K, V>,
        private val interval: Duration,
        private val scope: CoroutineScope
    ) : Cache<K, V> {
        private val listeners: ConcurrentLinkedQueue<(K, V?) -> Unit> = ConcurrentLinkedQueue()

        private val jobs: ConcurrentHashMap<K, Job> = ConcurrentHashMap()

        override suspend fun get(key: K): V? =
            underlying.get(key)

        override suspend fun getOrLoad(key: K, load: suspend (K) -> V?): V? =
            underlying.getOrLoad(key, load)?.also {
                if (!jobs.containsKey(key)) {
                    refresh(key, load)
                }
            }

        override suspend fun remove(key: K) {
            jobs.remove(key)?.cancel()
            underlying.remove(key)
        }

        fun registerOnEntryRefreshedListener(listener: (K, V?) -> Unit) {
            listeners.add(listener)
        }

        fun unregisterOnEntryRefreshedListener(listener: (K, V?) -> Unit) {
            listeners.remove(listener)
        }

        private fun refresh(key: K, load: suspend (K) -> V?) {
            try {
                val job = scope.launch {
                    delay(timeMillis = interval.toMillis())
                    underlying.remove(key)
                    val refreshedValue = underlying.getOrLoad(key, load)
                    listeners.forEach { it(key, refreshedValue) }
                    refresh(key, load)
                }

                jobs[key] = job
            } catch (_: CancellationException) {
                // do nothing
            }
        }
    }
}
