package stasis.test.client_android.lib.discovery.http

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import stasis.client_android.lib.discovery.ServiceDiscoveryClient
import stasis.client_android.lib.discovery.ServiceDiscoveryRequest
import stasis.client_android.lib.discovery.ServiceDiscoveryResult
import stasis.client_android.lib.discovery.http.HttpServiceDiscoveryClient
import stasis.client_android.lib.security.HttpCredentials
import stasis.client_android.lib.utils.Try.Success

class HttpServiceDiscoveryClientSpec : WordSpec({
    "A HttpServiceDiscoveryClient" should {

        fun createServer(withResponse: MockResponse? = null): MockWebServer {
            val server = MockWebServer()
            withResponse?.let { server.enqueue(it) }
            server.start()

            return server
        }

        "support retrieving latest service discovery information" {
            val apiCredentials = HttpCredentials.BasicHttpCredentials(
                username = "some-user",
                password = "some-password"
            )

            val attributes = object : ServiceDiscoveryClient.Attributes {
                override fun asServiceDiscoveryRequest(isInitialRequest: Boolean): ServiceDiscoveryRequest =
                    ServiceDiscoveryRequest(isInitialRequest = isInitialRequest, attributes = emptyMap())
            }

            val api = createServer(
                withResponse = MockResponse().setBody("""{"result":"keep-existing"}""")
            )

            val client = HttpServiceDiscoveryClient(
                apiUrl = api.url("/").toString(),
                credentials = { apiCredentials },
                attributes = attributes
            )

            client.latest(isInitialRequest = false) shouldBe (Success(ServiceDiscoveryResult.KeepExisting))
        }
    }
})
