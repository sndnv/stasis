package stasis.test.client_android.lib.api.clients

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.api.clients.ServiceApiClientFactory
import stasis.client_android.lib.discovery.ServiceApiClient
import stasis.client_android.lib.discovery.ServiceApiEndpoint
import stasis.client_android.lib.model.core.networking.EndpointAddress
import stasis.test.client_android.lib.mocks.MockServerApiEndpointClient
import stasis.test.client_android.lib.mocks.MockServerCoreEndpointClient
import stasis.test.client_android.lib.mocks.MockServiceDiscoveryClient

class ServiceApiClientFactorySpec : WordSpec({
    "A ServiceApiClientFactory" should {
        "support creating API clients" {
            val expectedClient = MockServerApiEndpointClient()

            val factory = ServiceApiClientFactory(
                createServerCoreEndpointClient = { throw UnsupportedOperationException() },
                createServerApiEndpointClient = { _, _ -> expectedClient },
                createServiceDiscoveryClient = { throw UnsupportedOperationException() }
            )

            val actualClient = factory.create(
                endpoint = ServiceApiEndpoint.Api(uri = "test-uri"),
                coreClient = MockServerCoreEndpointClient()
            )

            actualClient shouldBe (expectedClient)
        }

        "fail to create API clients if an invalid core client is provided" {
            val factory = ServiceApiClientFactory(
                createServerCoreEndpointClient = { throw UnsupportedOperationException() },
                createServerApiEndpointClient = { _, _ -> MockServerApiEndpointClient() },
                createServiceDiscoveryClient = { throw UnsupportedOperationException() }
            )

            val e = shouldThrow<IllegalArgumentException> {
                factory.create(
                    endpoint = ServiceApiEndpoint.Api(uri = "test-uri"),
                    coreClient = object : ServiceApiClient {}
                )
            }

            e.message shouldBe ("Cannot create API endpoint client with core client of type []")
        }

        "support creating core clients" {
            val expectedClient = MockServerCoreEndpointClient()

            val factory = ServiceApiClientFactory(
                createServerCoreEndpointClient = { expectedClient },
                createServerApiEndpointClient = { _, _ -> throw UnsupportedOperationException() },
                createServiceDiscoveryClient = { throw UnsupportedOperationException() }
            )

            val actualClient = factory.create(
                endpoint = ServiceApiEndpoint.Core(address = EndpointAddress.HttpEndpointAddress(uri = "test-uri"))
            )

            actualClient shouldBe (expectedClient)
        }

        "fail to create core clients if an unsupported core address is provided" {
            val expectedClient = MockServerCoreEndpointClient()

            val factory = ServiceApiClientFactory(
                createServerCoreEndpointClient = { expectedClient },
                createServerApiEndpointClient = { _, _ -> throw UnsupportedOperationException() },
                createServiceDiscoveryClient = { throw UnsupportedOperationException() }
            )

            val e = shouldThrow<IllegalArgumentException> {
                factory.create(
                    endpoint = ServiceApiEndpoint.Core(
                        address = EndpointAddress.GrpcEndpointAddress(
                            host = "localhost",
                            port = 1234,
                            tlsEnabled = false
                        )
                    )
                )
            }

            e.message shouldBe ("Cannot create core endpoint client for address of type [GrpcEndpointAddress]")
        }

        "support creating discovery clients" {
            val expectedClient = MockServiceDiscoveryClient()

            val factory = ServiceApiClientFactory(
                createServerCoreEndpointClient = { throw UnsupportedOperationException() },
                createServerApiEndpointClient = { _, _ -> throw UnsupportedOperationException() },
                createServiceDiscoveryClient = { expectedClient }
            )

            val actualClient = factory.create(
                endpoint = ServiceApiEndpoint.Discovery(uri = "test-uri")
            )

            actualClient shouldBe (expectedClient)
        }
    }
})
