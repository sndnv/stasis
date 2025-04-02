package stasis.test.client_android.lib.api.clients

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.beInstanceOf
import stasis.client_android.lib.api.clients.Clients
import stasis.client_android.lib.api.clients.ServerApiEndpointClient
import stasis.client_android.lib.discovery.ServiceApiClient
import stasis.client_android.lib.discovery.exceptions.DiscoveryFailure
import stasis.client_android.lib.discovery.providers.client.ServiceDiscoveryProvider
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.lib.utils.Try.Success
import stasis.test.client_android.lib.mocks.MockServerApiEndpointClient
import stasis.test.client_android.lib.mocks.MockServerCoreEndpointClient

class ClientsSpec : WordSpec({
    "Clients" should {
        "provide static clients" {
            val staticApiClient = MockServerApiEndpointClient()
            val staticCoreClient = MockServerCoreEndpointClient()

            val static = Clients(api = staticApiClient, core = staticCoreClient)
            static should beInstanceOf<Clients.Static>()

            static.api shouldBe (staticApiClient)
            static.core shouldBe (staticCoreClient)

            static.withDiscovery(discovery = Failure(RuntimeException("Test failure")))

            static.api shouldBe (staticApiClient)
            static.core shouldBe (staticCoreClient)
        }

        "provide discovery-based clients" {
            val staticApiClient = MockServerApiEndpointClient()
            val staticCoreClient = MockServerCoreEndpointClient()

            val discoveryProvider = object : ServiceDiscoveryProvider {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ServiceApiClient> latest(forClass: Class<T>): T =
                    if (forClass == ServerApiEndpointClient::class.java) {
                        MockServerApiEndpointClient() as T
                    } else {
                        MockServerCoreEndpointClient() as T
                    }

                override fun stop() = Unit
            }

            val discovery = Clients.discovered()
            discovery should beInstanceOf<Clients.Discovered>()
            val e1 = shouldThrow<DiscoveryFailure> { discovery.api }
            val e2 = shouldThrow<DiscoveryFailure> { discovery.core }

            e1.message shouldBe ("No discovery provider found")
            e2.message shouldBe ("No discovery provider found")

            discovery.withDiscovery(discovery = Failure(RuntimeException("Test failure")))
            shouldThrow<RuntimeException> { discovery.api }
            shouldThrow<RuntimeException> { discovery.core }

            discovery.withDiscovery(discovery = Success(discoveryProvider))
            discovery.api shouldNotBe staticApiClient
            discovery.core shouldNotBe staticCoreClient
        }
    }
})
