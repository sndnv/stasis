package stasis.test.client_android.lib.discovery.providers.client

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.beInstanceOf
import kotlinx.coroutines.delay
import stasis.client_android.lib.discovery.ServiceApiClient
import stasis.client_android.lib.discovery.ServiceApiEndpoint
import stasis.client_android.lib.discovery.ServiceDiscoveryClient
import stasis.client_android.lib.discovery.ServiceDiscoveryResult
import stasis.client_android.lib.discovery.exceptions.DiscoveryFailure
import stasis.client_android.lib.discovery.providers.client.ServiceDiscoveryProvider
import stasis.client_android.lib.model.core.networking.EndpointAddress
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Failure
import stasis.test.client_android.lib.eventually
import stasis.test.client_android.lib.mocks.MockServiceDiscoveryClient
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class ServiceDiscoveryProviderSpec : WordSpec({
    "A ServiceDiscoveryProvider" should {

        class TestApiClient : ServiceApiClient

        class TestCoreClient : ServiceApiClient

        fun createClients(
            initialDiscoveryResult: ServiceDiscoveryResult,
            nextDiscoveryResult: ServiceDiscoveryResult
        ): List<ServiceApiClient> = listOf(
            MockServiceDiscoveryClient(
                initialDiscoveryResult = initialDiscoveryResult,
                nextDiscoveryResult = nextDiscoveryResult
            )
        )

        fun createClientFactory(
            nextDiscoveryResult: ServiceDiscoveryResult = ServiceDiscoveryResult.KeepExisting
        ): ServiceApiClient.Factory =
            object : ServiceApiClient.Factory {
                override fun create(endpoint: ServiceApiEndpoint.Api, coreClient: ServiceApiClient): ServiceApiClient =
                    TestApiClient()

                override fun create(endpoint: ServiceApiEndpoint.Core): ServiceApiClient =
                    TestCoreClient()

                override fun create(endpoint: ServiceApiEndpoint.Discovery): ServiceApiClient =
                    MockServiceDiscoveryClient(
                        initialDiscoveryResult = nextDiscoveryResult,
                        nextDiscoveryResult = nextDiscoveryResult
                    )
            }

        suspend fun <T> managedProvider(provider: ServiceDiscoveryProvider, block: suspend () -> T): T =
            try {
                block()
            } finally {
                provider.stop()
            }

        "support providing existing clients when discovery is not active" {
            val provider = ServiceDiscoveryProvider(
                initialDelay = Duration.ofSeconds(3),
                interval = Duration.ofSeconds(3),
                initialClients = createClients(
                    initialDiscoveryResult = ServiceDiscoveryResult.KeepExisting,
                    nextDiscoveryResult = ServiceDiscoveryResult.KeepExisting
                ),
                clientFactory = createClientFactory(),
                coroutineScope = testScope
            ).get()

            managedProvider(provider) {
                provider should beInstanceOf<ServiceDiscoveryProvider.Disabled>()

                provider.latest(ServiceDiscoveryClient::class.java) should beInstanceOf<MockServiceDiscoveryClient>()

                shouldThrow<DiscoveryFailure> { provider.latest(TestApiClient::class.java) }
                shouldThrow<DiscoveryFailure> { provider.latest(TestCoreClient::class.java) }
            }
        }

        "support providing new clients when discovery is active" {
            val provider = ServiceDiscoveryProvider(
                initialDelay = Duration.ofSeconds(3),
                interval = Duration.ofSeconds(3),
                initialClients = createClients(
                    initialDiscoveryResult = ServiceDiscoveryResult.SwitchTo(
                        endpoints = ServiceDiscoveryResult.Endpoints(
                            api = ServiceApiEndpoint.Api(uri = "test-rui"),
                            core = ServiceApiEndpoint.Core(address = EndpointAddress.HttpEndpointAddress(uri = "test-rui")),
                            discovery = ServiceApiEndpoint.Discovery(uri = "test-rui")
                        ),
                        recreateExisting = false
                    ),
                    nextDiscoveryResult = ServiceDiscoveryResult.KeepExisting
                ),
                clientFactory = createClientFactory(),
                coroutineScope = testScope
            ).get()

            managedProvider(provider) {
                provider should beInstanceOf<ServiceDiscoveryProvider.Default>()

                provider.latest(ServiceDiscoveryClient::class.java) should beInstanceOf<MockServiceDiscoveryClient>()

                shouldNotThrow<Throwable> { provider.latest(TestApiClient::class.java) }
                shouldNotThrow<Throwable> { provider.latest(TestCoreClient::class.java) }
            }
        }

        "periodically refresh list of endpoints and clients (with result=keep-existing)" {
            fun createResult(recreateExisting: Boolean = false): ServiceDiscoveryResult =
                ServiceDiscoveryResult.SwitchTo(
                    endpoints = ServiceDiscoveryResult.Endpoints(
                        api = ServiceApiEndpoint.Api(uri = java.util.UUID.randomUUID().toString()),
                        core = ServiceApiEndpoint.Core(
                            address = EndpointAddress.HttpEndpointAddress(
                                uri = java.util.UUID.randomUUID().toString()
                            )
                        ),
                        discovery = ServiceApiEndpoint.Discovery(uri = java.util.UUID.randomUUID().toString())
                    ),
                    recreateExisting = recreateExisting
                )

            val provider = ServiceDiscoveryProvider(
                initialDelay = Duration.ofMillis(200),
                interval = Duration.ofMillis(200),
                initialClients = createClients(
                    initialDiscoveryResult = createResult(),
                    nextDiscoveryResult = createResult()
                ),
                clientFactory = createClientFactory(),
                coroutineScope = testScope
            ).get()

            managedProvider(provider) {
                provider should beInstanceOf<ServiceDiscoveryProvider.Default>()

                val initialDiscoveryClient = provider.latest(ServiceDiscoveryClient::class.java)
                val initialApiClient = provider.latest(TestApiClient::class.java)
                val initialCoreClient = provider.latest(TestCoreClient::class.java)

                delay(timeMillis = 100)

                provider.latest(ServiceDiscoveryClient::class.java) shouldBe (initialDiscoveryClient)
                provider.latest(TestApiClient::class.java) shouldBe (initialApiClient)
                provider.latest(TestCoreClient::class.java) shouldBe (initialCoreClient)
            }
        }

        "periodically refresh list of endpoints and clients (with result=switch-to)" {
            fun createResult(recreateExisting: Boolean = false): ServiceDiscoveryResult =
                ServiceDiscoveryResult.SwitchTo(
                    endpoints = ServiceDiscoveryResult.Endpoints(
                        api = ServiceApiEndpoint.Api(uri = java.util.UUID.randomUUID().toString()),
                        core = ServiceApiEndpoint.Core(
                            address = EndpointAddress.HttpEndpointAddress(
                                uri = java.util.UUID.randomUUID().toString()
                            )
                        ),
                        discovery = ServiceApiEndpoint.Discovery(uri = java.util.UUID.randomUUID().toString())
                    ),
                    recreateExisting = recreateExisting
                )

            val provider = ServiceDiscoveryProvider(
                initialDelay = Duration.ofMillis(200),
                interval = Duration.ofMillis(200),
                initialClients = createClients(
                    initialDiscoveryResult = createResult(),
                    nextDiscoveryResult = createResult()
                ),
                clientFactory = createClientFactory(nextDiscoveryResult = createResult(recreateExisting = true)),
                coroutineScope = testScope
            ).get()

            managedProvider(provider) {
                provider should beInstanceOf<ServiceDiscoveryProvider.Default>()

                val initialDiscoveryClient = provider.latest(ServiceDiscoveryClient::class.java)
                val initialApiClient = provider.latest(TestApiClient::class.java)
                val initialCoreClient = provider.latest(TestCoreClient::class.java)

                delay(timeMillis = 100)

                provider.latest(ServiceDiscoveryClient::class.java) shouldBe (initialDiscoveryClient)
                provider.latest(TestApiClient::class.java) shouldBe (initialApiClient)
                provider.latest(TestCoreClient::class.java) shouldBe (initialCoreClient)

                eventually {
                    provider.latest(ServiceDiscoveryClient::class.java) shouldNotBe (initialDiscoveryClient)
                    provider.latest(TestApiClient::class.java) shouldNotBe (initialApiClient)
                    provider.latest(TestCoreClient::class.java) shouldNotBe (initialCoreClient)
                }
            }
        }

        "not recreate clients for same endpoints" {
            fun createResult(recreateExisting: Boolean = false): ServiceDiscoveryResult =
                ServiceDiscoveryResult.SwitchTo(
                    endpoints = ServiceDiscoveryResult.Endpoints(
                        api = ServiceApiEndpoint.Api(uri = java.util.UUID.randomUUID().toString()),
                        core = ServiceApiEndpoint.Core(
                            address = EndpointAddress.HttpEndpointAddress(
                                uri = java.util.UUID.randomUUID().toString()
                            )
                        ),
                        discovery = ServiceApiEndpoint.Discovery(uri = java.util.UUID.randomUUID().toString())
                    ),
                    recreateExisting = recreateExisting
                )

            val provider = ServiceDiscoveryProvider(
                initialDelay = Duration.ofMillis(100),
                interval = Duration.ofMillis(100),
                initialClients = createClients(
                    initialDiscoveryResult = createResult(),
                    nextDiscoveryResult = createResult()
                ),
                clientFactory = createClientFactory(nextDiscoveryResult = createResult()),
                coroutineScope = testScope
            ).get()

            managedProvider(provider) {
                provider should beInstanceOf<ServiceDiscoveryProvider.Default>()

                val initialDiscoveryClient = provider.latest(ServiceDiscoveryClient::class.java)
                val initialApiClient = provider.latest(TestApiClient::class.java)
                val initialCoreClient = provider.latest(TestCoreClient::class.java)

                delay(timeMillis = 200)

                provider.latest(ServiceDiscoveryClient::class.java) shouldNotBe (initialDiscoveryClient)
                provider.latest(TestApiClient::class.java) shouldNotBe (initialApiClient)
                provider.latest(TestCoreClient::class.java) shouldNotBe (initialCoreClient)

                val latestDiscoveryClient = provider.latest(ServiceDiscoveryClient::class.java)
                val latestApiClient = provider.latest(TestApiClient::class.java)
                val latestCoreClient = provider.latest(TestCoreClient::class.java)

                delay(timeMillis = 200)

                eventually {
                    latestDiscoveryClient shouldNotBe initialDiscoveryClient
                    latestApiClient shouldNotBe initialApiClient
                    latestCoreClient shouldNotBe initialCoreClient

                    provider.latest(ServiceDiscoveryClient::class.java) shouldBe (latestDiscoveryClient)
                    provider.latest(TestApiClient::class.java) shouldBe (latestApiClient)
                    provider.latest(TestCoreClient::class.java) shouldBe (latestCoreClient)
                }
            }
        }

        "fail to retrieve unsupported client types" {
            val provider = ServiceDiscoveryProvider(
                initialDelay = Duration.ofSeconds(3),
                interval = Duration.ofSeconds(3),
                initialClients = createClients(
                    initialDiscoveryResult = ServiceDiscoveryResult.KeepExisting,
                    nextDiscoveryResult = ServiceDiscoveryResult.KeepExisting
                ),
                clientFactory = createClientFactory(),
                coroutineScope = testScope
            ).get()


            managedProvider(provider) {
                provider should beInstanceOf<ServiceDiscoveryProvider.Disabled>()

                val e = shouldThrow<DiscoveryFailure> { provider.latest(TestApiClient::class.java) }

                e.message shouldBe ("Service client [${TestApiClient::class.java.name}] was not found")
            }
        }

        "handle discovery failures" {
            val clientCalls = AtomicInteger(0)

            val provider = ServiceDiscoveryProvider(
                initialDelay = Duration.ofMillis(100),
                interval = Duration.ofMillis(200),
                initialClients = createClients(
                    initialDiscoveryResult = ServiceDiscoveryResult.SwitchTo(
                        endpoints = ServiceDiscoveryResult.Endpoints(
                            api = ServiceApiEndpoint.Api(uri = "test-rui"),
                            core = ServiceApiEndpoint.Core(address = EndpointAddress.HttpEndpointAddress(uri = "test-rui")),
                            discovery = ServiceApiEndpoint.Discovery(uri = "test-rui")
                        ),
                        recreateExisting = false
                    ),
                    nextDiscoveryResult = ServiceDiscoveryResult.KeepExisting
                ),
                clientFactory = object : ServiceApiClient.Factory {
                    override fun create(
                        endpoint: ServiceApiEndpoint.Api,
                        coreClient: ServiceApiClient
                    ): ServiceApiClient =
                        TestApiClient()

                    override fun create(endpoint: ServiceApiEndpoint.Core): ServiceApiClient =
                        TestCoreClient()

                    override fun create(endpoint: ServiceApiEndpoint.Discovery): ServiceApiClient =
                        object : ServiceDiscoveryClient {
                            override val attributes: ServiceDiscoveryClient.Attributes =
                                MockServiceDiscoveryClient.TestAttributes(a = "b")

                            override suspend fun latest(isInitialRequest: Boolean): Try<ServiceDiscoveryResult> {
                                clientCalls.incrementAndGet()
                                return Failure(RuntimeException("Test failure"))
                            }
                        }
                },
                coroutineScope = testScope
            ).get()

            managedProvider(provider) {
                clientCalls.get() shouldBe (0)

                delay(timeMillis = 100)

                clientCalls.get() shouldBe (0)

                delay(timeMillis = 400)

                clientCalls.get() shouldBeGreaterThanOrEqual (3) // the interval is reduced so more requests should be made
            }
        }

        "support creating the provider asynchronously" {
            val providerRef = AtomicReference<Try<ServiceDiscoveryProvider>?>(null)

            ServiceDiscoveryProvider.create(
                initialDelay = Duration.ofSeconds(3),
                interval = Duration.ofSeconds(3),
                initialClients = createClients(
                    initialDiscoveryResult = ServiceDiscoveryResult.KeepExisting,
                    nextDiscoveryResult = ServiceDiscoveryResult.KeepExisting
                ),
                clientFactory = createClientFactory(),
                onCreated = { providerRef.set(it) },
                coroutineScope = testScope
            )

            providerRef.get() shouldBe (null)

            eventually {
                val provider = providerRef.get()?.get()
                provider shouldNotBe (null)

                managedProvider(provider!!) {
                    provider should beInstanceOf<ServiceDiscoveryProvider.Disabled>()
                }
            }
        }
    }
})
