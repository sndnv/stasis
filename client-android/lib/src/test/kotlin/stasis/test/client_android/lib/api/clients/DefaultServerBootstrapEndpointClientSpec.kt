package stasis.test.client_android.lib.api.clients

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import stasis.client_android.lib.api.clients.DefaultServerBootstrapEndpointClient
import stasis.client_android.lib.model.server.devices.DeviceBootstrapParameters
import stasis.client_android.lib.security.HttpCredentials
import java.util.UUID

class DefaultServerBootstrapEndpointClientSpec : WordSpec({
    "DefaultServerBootstrapEndpointClient" should {
        "execute device bootstrap" {
            val testCode = "test-code"

            val testParams = DeviceBootstrapParameters(
                authentication = DeviceBootstrapParameters.Authentication(
                    tokenEndpoint = "http://localhost:1234",
                    clientId = UUID.randomUUID().toString(),
                    clientSecret = "test-secret",
                    useQueryString = true,
                    scopes = DeviceBootstrapParameters.Scopes(
                        api = "urn:stasis:identity:audience:server-api",
                        core = "urn:stasis:identity:audience:${UUID.randomUUID()}"
                    )
                ),
                serverApi = DeviceBootstrapParameters.ServerApi(
                    url = "http://localhost:5678",
                    user = UUID.randomUUID().toString(),
                    userSalt = "test-salt",
                    device = UUID.randomUUID().toString()
                ),
                serverCore = DeviceBootstrapParameters.ServerCore(
                    address = "http://localhost:5679"
                )
            )

            val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val expectedResponse = moshi.adapter(DeviceBootstrapParameters::class.java).toJson(testParams)

            val server = MockWebServer()
            server.enqueue(MockResponse().setBody(expectedResponse))
            server.start()

            val endpointClient = DefaultServerBootstrapEndpointClient(
                serverBootstrapUrl = server.url("/").toString()
            )

            val actualParams = endpointClient.execute(testCode)
            actualParams shouldBe (testParams)

            val request = server.takeRequest()
            request.path shouldBe ("/devices/execute")
            request.method shouldBe ("PUT")
            request.body.size shouldBe (0)
            request.headers[HttpCredentials.AuthorizationHeader] shouldBe ("Bearer $testCode")

            server.shutdown()
        }
    }
})
