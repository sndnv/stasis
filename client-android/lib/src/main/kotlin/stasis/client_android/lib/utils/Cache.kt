package stasis.client_android.lib.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import stasis.client_android.lib.utils.NonFatal.nonFatal
import stasis.client_android.lib.utils.Try.Companion.flatMap
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.math.min
import kotlin.streams.toList as kotlinToList

interface Cache<K : Any, V> {

    /**
     * Retrieves the cached value associated with [key], if any.
     *
     * @param key the key associated with the needed value
     * @return the cached value or `null` if none was found
     */
    suspend fun get(key: K): V?

    /**
     * Puts a new [value] with the provided [key] in the cache.
     *
     * @param key the key associated with the value
     * @param value the value to insert
     */
    suspend fun put(key: K, value: V)

    /**
     * Puts the provided entries in the cache.
     *
     * @param entries entries to insert
     */
    suspend fun put(entries: kotlin.collections.Map<K, V>)

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
     * Retrieves all cached values.
     */
    suspend fun all(): kotlin.collections.Map<K, V>

    /**
     * Removes all cached values.
     */
    suspend fun clear()

    /**
     * Simple in-memory cache backed by [ConcurrentHashMap].
     */
    class Map<K : Any, V> : Cache<K, V> {
        private val map: ConcurrentHashMap<K, V> = ConcurrentHashMap()

        override suspend fun get(key: K): V? = map[key]

        override suspend fun put(key: K, value: V) {
            map[key] = value
        }

        override suspend fun put(entries: kotlin.collections.Map<K, V>) =
            map.putAll(entries)

        override suspend fun getOrLoad(key: K, load: suspend (K) -> V?): V? =
            get(key) ?: load(key)?.also { map[key] = it }

        override suspend fun remove(key: K) {
            map.remove(key)
        }

        override suspend fun all(): kotlin.collections.Map<K, V> =
            map

        override suspend fun clear() =
            map.clear()
    }

    /**
     * File-based cache.
     *
     * @param target directory for storing state files
     * @param serdes key and value de/serializers
     */
    class File<K : Any, V>(
        private val target: Path,
        private val serdes: Serdes<K, V>
    ) : Cache<K, V> {
        private val inMemory = Map<K, V>()

        override suspend fun get(key: K): V? = inMemory.get(key) ?: Try {
            key.asStateFile().readBytes()
        }.flatMap { serdes.deserializeValue(it) }.toOption()?.also { inMemory.put(key, it) }

        override suspend fun put(key: K, value: V) {
            inMemory.put(key, value)
            key.asStateFile().writeBytes(serdes.serializeValue(value))
        }

        override suspend fun put(entries: kotlin.collections.Map<K, V>) =
            entries.forEach { put(it.key, it.value) }

        override suspend fun getOrLoad(key: K, load: suspend (K) -> V?): V? =
            get(key) ?: load(key)?.also { put(key, it) }

        override suspend fun remove(key: K) {
            inMemory.remove(key)
            key.asStateFile().deleteIfExists()
        }

        override suspend fun all(): kotlin.collections.Map<K, V> =
            keys()
                .mapNotNull { key -> get(key)?.let { value: V -> key to value } }
                .toMap()

        override suspend fun clear() =
            keys().forEach { remove(it) }

        private fun K.asStateFile(): Path {
            Files.createDirectories(target)
            return target.resolve(serdes.serializeKey(this))
        }

        private fun keys(): List<K> =
            Try { Files.list(target).kotlinToList() }
                .getOrElse { emptyList() }
                .mapNotNull { serdes.deserializeKey(it.fileName.toString()) }

        interface Serdes<K : Any, V> {
            fun serializeKey(key: K): String
            fun deserializeKey(key: String): K?
            fun serializeValue(value: V): ByteArray
            fun deserializeValue(value: ByteArray): Try<V>
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

        private val jobs: ConcurrentHashMap<K, Job> = ConcurrentHashMap()

        override suspend fun get(key: K): V? =
            underlying.get(key)

        override suspend fun put(key: K, value: V) {
            underlying.put(key, value)
            expire(key)
        }

        override suspend fun put(entries: kotlin.collections.Map<K, V>) {
            entries.forEach { put(it.key, it.value) }
        }

        override suspend fun getOrLoad(key: K, load: suspend (K) -> V?): V? =
            underlying.getOrLoad(key, load = { k -> load(k)?.also { expire(k) } })

        override suspend fun remove(key: K) {
            jobs.remove(key)?.cancel()
            underlying.remove(key)
        }

        override suspend fun all(): kotlin.collections.Map<K, V> =
            underlying.all()

        override suspend fun clear() {
            jobs.forEach { it.value.cancel() }
            jobs.clear()
            underlying.clear()
        }

        fun registerOnEntryExpiredListener(listener: (K) -> Unit) {
            listeners.add(listener)
        }

        fun unregisterOnEntryExpiredListener(listener: (K) -> Unit) {
            listeners.remove(listener)
        }

        private fun expire(key: K) {
            try {
                val job = scope.launch {
                    delay(timeMillis = expiration.toMillis())
                    remove(key)
                    listeners.forEach { it(key) }
                }

                jobs[key] = job
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

        override suspend fun put(key: K, value: V) {
            underlying.put(key, value)
        }

        override suspend fun put(entries: kotlin.collections.Map<K, V>) {
            entries.forEach { put(it.key, it.value) }
        }

        override suspend fun getOrLoad(key: K, load: suspend (K) -> V?): V? =
            underlying.getOrLoad(key, load)?.also {
                if (!jobs.containsKey(key)) {
                    refresh(key, load, interval)
                }
            }

        override suspend fun remove(key: K) {
            jobs.remove(key)?.cancel()
            underlying.remove(key)
        }

        override suspend fun all(): kotlin.collections.Map<K, V> =
            underlying.all()

        override suspend fun clear() {
            jobs.forEach { it.value.cancel() }
            jobs.clear()
            underlying.clear()
        }

        fun registerOnEntryRefreshedListener(listener: (K, V?) -> Unit) {
            listeners.add(listener)
        }

        fun unregisterOnEntryRefreshedListener(listener: (K, V?) -> Unit) {
            listeners.remove(listener)
        }

        private fun refresh(key: K, load: suspend (K) -> V?, refreshInterval: Duration) {
            try {
                val job = scope.launch {
                    delay(timeMillis = refreshInterval.toMillis())

                    try {
                        val refreshedValue = load(key)

                        when (refreshedValue) {
                            null -> underlying.remove(key)
                            else -> underlying.put(key, refreshedValue)
                        }

                        listeners.forEach { it(key, refreshedValue) }
                        refresh(key, load, interval)
                    } catch (e: Throwable) {
                        e.nonFatal()
                        val nextRefresh = Duration.ofMillis(
                            min(
                                interval.dividedBy(RefreshIntervalOnFailureReduction).toMillis(),
                                MaxRefreshIntervalOnFailure.toMillis()
                            )
                        )
                        refresh(key, load, nextRefresh)
                    }
                }

                jobs[key] = job
            } catch (_: CancellationException) {
                // do nothing
            }
        }

        companion object {
            const val RefreshIntervalOnFailureReduction: Long = 10L
            val MaxRefreshIntervalOnFailure: Duration = Duration.ofSeconds(5)
        }
    }

    /**
     * Statistics-tracking cache.
     *
     * Statistics about cache hits and misses are kept and provided.
     *
     * @param underlying cache that handles the actual data storage
     */
    class Tracking<K : Any, V>(
        private val underlying: Cache<K, V>,
    ) : Cache<K, V> {
        private val stats: Statistics = Statistics()

        /**
         * Provides the current number of cache hits.
         */
        val hits: Long
            get() = stats.hits

        /**
         * Provides the current number of cache misses.
         */
        val misses: Long
            get() = stats.misses

        override suspend fun get(key: K): V? {
            val result = underlying.get(key = key)

            when (result) {
                null -> stats.miss()
                else -> stats.hit()
            }

            return result
        }

        override suspend fun put(key: K, value: V) =
            underlying.put(key = key, value = value)

        override suspend fun put(entries: kotlin.collections.Map<K, V>) =
            underlying.put(entries = entries)

        override suspend fun getOrLoad(key: K, load: suspend (K) -> V?): V? =
            get(key) ?: load(key)?.also { put(key, it) }

        override suspend fun remove(key: K) =
            underlying.remove(key = key)

        override suspend fun all(): kotlin.collections.Map<K, V> =
            underlying.all()

        override suspend fun clear() =
            underlying.clear()
    }

    private class Statistics {
        private val hitsRef: AtomicLong = AtomicLong(0)
        private val missesRef: AtomicLong = AtomicLong(0)

        fun hit() {
            hitsRef.incrementAndGet()
        }

        fun miss() {
            missesRef.incrementAndGet()
        }

        val hits: Long
            get() = hitsRef.get()

        val misses: Long
            get() = missesRef.get()
    }
}
