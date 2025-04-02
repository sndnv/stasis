package stasis.client_android.lib.discovery.providers.client

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import stasis.client_android.lib.discovery.ServiceApiClient
import stasis.client_android.lib.discovery.ServiceDiscoveryClient
import stasis.client_android.lib.discovery.ServiceDiscoveryResult
import stasis.client_android.lib.discovery.exceptions.DiscoveryFailure
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Companion.map
import java.lang.Long.max
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import kotlin.coroutines.cancellation.CancellationException

interface ServiceDiscoveryProvider {
    fun <T : ServiceApiClient> latest(forClass: Class<T>): T
    fun stop()

    class Disabled(
        private val initialClients: List<ServiceApiClient>
    ) : ServiceDiscoveryProvider {
        override fun <T : ServiceApiClient> latest(forClass: Class<T>): T =
            extractClient(from = initialClients, forClass = forClass)

        override fun stop() = Unit // do nothing
    }

    class Default(
        private val initialDelay: Duration,
        private val interval: Duration,
        initialClients: Map<String, ServiceApiClient>,
        private val clientFactory: ServiceApiClient.Factory,
        coroutineScope: CoroutineScope
    ) : ServiceDiscoveryProvider {
        private val job: Job

        private val clients: ConcurrentHashMap<String, ServiceApiClient> = ConcurrentHashMap(initialClients)

        init {
            job = coroutineScope.launch {
                delay(timeMillis = initialDelay.toMillis())
                scheduleNext()
            }
        }

        override fun <T : ServiceApiClient> latest(forClass: Class<T>): T {
            val rnd = ThreadLocalRandom.current()
            val collected = clients.values.filterIsInstance(forClass)
            return extractClient(from = collected.shuffled(rnd), forClass = forClass)
        }

        override fun stop() {
            job.cancel()
        }

        private suspend fun scheduleNext(): Unit = withContext(Dispatchers.IO) {
            try {
                val result = discoverServices(
                    client = extractClient(from = clients.values.toList(), ServiceDiscoveryClient::class.java),
                    isInitialRequest = true
                ).get()

                when (result) {
                    is ServiceDiscoveryResult.KeepExisting -> Unit // do nothing
                    is ServiceDiscoveryResult.SwitchTo -> if (result.recreateExisting) {
                        clients.clear()
                        val core = clientFactory.create(result.endpoints.core)
                        clients[result.endpoints.core.id] = core
                        clients[result.endpoints.api.id] = clientFactory.create(result.endpoints.api, core)
                        clients[result.endpoints.discovery.id] = clientFactory.create(result.endpoints.discovery)
                    } else {
                        val core = clients.computeIfAbsent(result.endpoints.core.id) {
                            clientFactory.create(result.endpoints.core)
                        }

                        clients.computeIfAbsent(result.endpoints.api.id) {
                            clientFactory.create(
                                result.endpoints.api,
                                core
                            )
                        }

                        clients.computeIfAbsent(result.endpoints.discovery.id) {
                            clientFactory.create(result.endpoints.discovery)
                        }

                        clients.keys.filter { k ->
                            k != result.endpoints.core.id && k != result.endpoints.api.id && k != result.endpoints.discovery.id
                        }.forEach { k -> clients.remove(k) }
                    }
                }

                delay(timeMillis = fullInterval())
            } catch (_: CancellationException) {
                // do nothing
            } catch (_: Throwable) {
                delay(timeMillis = reducedInterval())
            } finally {
                scheduleNext()
            }
        }

        private fun fullInterval(): Long =
            fuzzyInterval(interval = interval.toMillis())

        private fun reducedInterval(): Long =
            max(
                fuzzyInterval(interval = interval.toMillis() / FailureIntervalReduction),
                initialDelay.toMillis()
            )

        @Suppress("MagicNumber")
        private fun fuzzyInterval(interval: Long): Long {
            val low = (interval - (interval * 0.02)).toLong()
            val high = (interval + (interval * 0.03)).toLong()

            return ThreadLocalRandom.current().nextLong(low, high)
        }
    }

    companion object {
        suspend operator fun invoke(
            initialDelay: Duration,
            interval: Duration,
            initialClients: List<ServiceApiClient>,
            clientFactory: ServiceApiClient.Factory,
            coroutineScope: CoroutineScope,
        ) = discoverServices(
            client = extractClient(from = initialClients, ServiceDiscoveryClient::class.java),
            isInitialRequest = true
        ).map {
            when (it) {
                is ServiceDiscoveryResult.KeepExisting -> Disabled(initialClients)

                is ServiceDiscoveryResult.SwitchTo -> {
                    val core = clientFactory.create(it.endpoints.core)

                    Default(
                        initialDelay = initialDelay,
                        interval = interval,
                        initialClients = mapOf(
                            it.endpoints.core.id to core,
                            it.endpoints.api.id to clientFactory.create(it.endpoints.api, core),
                            it.endpoints.discovery.id to clientFactory.create(it.endpoints.discovery)
                        ),
                        clientFactory = clientFactory,
                        coroutineScope = coroutineScope
                    )
                }
            }
        }

        fun create(
            initialDelay: Duration,
            interval: Duration,
            initialClients: List<ServiceApiClient>,
            clientFactory: ServiceApiClient.Factory,
            onCreated: (Try<ServiceDiscoveryProvider>) -> Unit,
            coroutineScope: CoroutineScope,
        ) {
            coroutineScope.launch {
                val provider = ServiceDiscoveryProvider(
                    initialDelay = initialDelay,
                    interval = interval,
                    initialClients = initialClients,
                    clientFactory = clientFactory,
                    coroutineScope = coroutineScope,
                )

                onCreated(provider)
            }
        }

        private suspend fun discoverServices(
            client: ServiceDiscoveryClient,
            isInitialRequest: Boolean
        ): Try<ServiceDiscoveryResult> = client.latest(isInitialRequest)

        private fun <T : ServiceApiClient> extractClient(from: List<ServiceApiClient>, forClass: Class<T>): T =
            when (val client = from.filterIsInstance(forClass).firstOrNull()) {
                null -> throw DiscoveryFailure("Service client [${forClass.name}] was not found")
                else -> client
            }

        const val FailureIntervalReduction: Int = 10
    }
}
