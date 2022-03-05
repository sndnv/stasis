package stasis.client_android.lib.utils

import java.util.concurrent.atomic.AtomicReference

interface Reference<O> {
    fun isEmpty(): Boolean
    fun isNotEmpty(): Boolean

    fun provided(): O?
    fun <T> provided(f: (O) -> T): T?

    fun required(): O
    fun <T> required(f: (O) -> T): T

    fun <O2> map(f: (O) -> O2): Reference<O2>

    fun <C> singleton(retrieveConfig: () -> C?, create: (C) -> O, destroy: (O?) -> Unit): Reference<O>

    class Empty<O> : Reference<O> {
        override fun isEmpty(): Boolean = true
        override fun isNotEmpty(): Boolean = false

        override fun provided(): O? = null
        override fun <T> provided(f: (O) -> T): T? = null

        override fun required(): O = throw IllegalArgumentException("Cannot require an empty reference")
        override fun <T> required(f: (O) -> T): T = throw IllegalArgumentException("Cannot require an empty reference")

        override fun <O2> map(f: (O) -> O2): Reference<O2> = Empty()

        override fun <C> singleton(retrieveConfig: () -> C?, create: (C) -> O, destroy: (O?) -> Unit): Reference<O> =
            Singleton(retrieveConfig, create, destroy)
    }

    class Singleton<C, O>(
        val retrieveConfig: () -> C?,
        val create: (C) -> O,
        val destroy: (O?) -> Unit
    ) : Reference<O> {
        private val singletonRef: AtomicReference<O?> = AtomicReference(null)

        override fun isEmpty(): Boolean = singletonRef.get() == null
        override fun isNotEmpty(): Boolean = !isEmpty()

        override fun provided(): O? =
            when (val config = retrieveConfig()) {
                null -> {
                    destroy(singletonRef.getAndSet(null))
                    null
                }

                else -> updateAndGetRef(config)
            }

        override fun <T> provided(f: (O) -> T): T? =
            provided()?.let(f)

        override fun required(): O {
            val result = provided()
            require(result != null) { "Expected result but none was provided" }
            return result
        }

        override fun <T> required(f: (O) -> T): T =
            f(required())

        override fun <O2> map(f: (O) -> O2): Reference<O2> = Singleton(
            retrieveConfig = retrieveConfig,
            create = { config -> f(updateAndGetRef(config)) },
            destroy = { destroy(singletonRef.get()) }
        )

        override fun <C> singleton(
            retrieveConfig: () -> C?,
            create: (C) -> O,
            destroy: (O?) -> Unit
        ): Reference<O> = this

        private fun updateAndGetRef(config: C): O {
            val singleton = singletonRef.updateAndGet { existingSingleton ->
                when (existingSingleton) {
                    null -> create(config)
                    else -> existingSingleton
                }
            }

            require(singleton != null) { "Expected an object but none was provided" }

            return singleton
        }
    }

    companion object {
        fun <O> empty(): Empty<O> = Empty()
    }
}
