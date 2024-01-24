package stasis.test.client_android.lib.api.clients

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.beInstanceOf
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.fail
import stasis.client_android.lib.api.clients.DefaultServerBootstrapEndpointClient
import stasis.client_android.lib.api.clients.exceptions.EndpointFailure
import stasis.client_android.lib.api.clients.exceptions.InvalidBootstrapCodeFailure
import stasis.client_android.lib.model.server.devices.DeviceBootstrapParameters
import stasis.client_android.lib.security.HttpCredentials
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.lib.utils.Try.Success
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
                    address = "http://localhost:5679",
                    nodeId = "test-node"
                ),
                secrets = DeviceBootstrapParameters.SecretsConfig(
                    derivation = DeviceBootstrapParameters.SecretsConfig.Derivation(
                        encryption = DeviceBootstrapParameters.SecretsConfig.Derivation.Encryption(
                            secretSize = 16,
                            iterations = 100000,
                            saltPrefix = "test-prefix"
                        ),
                        authentication = DeviceBootstrapParameters.SecretsConfig.Derivation.Authentication(
                            enabled = true,
                            secretSize = 16,
                            iterations = 100000,
                            saltPrefix = "test-prefix"
                        )
                    ),
                    encryption = DeviceBootstrapParameters.SecretsConfig.Encryption(
                        file = DeviceBootstrapParameters.SecretsConfig.Encryption.File(
                            keySize = 16,
                            ivSize = 12
                        ),
                        metadata = DeviceBootstrapParameters.SecretsConfig.Encryption.Metadata(
                            keySize = 16,
                            ivSize = 12
                        ),
                        deviceSecret = DeviceBootstrapParameters.SecretsConfig.Encryption.DeviceSecret(
                            keySize = 16,
                            ivSize = 12
                        )
                    )
                )
            )

            val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val expectedResponse =
                moshi.adapter(DeviceBootstrapParameters::class.java).toJson(testParams)

            val server = MockWebServer()
            server.enqueue(MockResponse().setBody(expectedResponse))
            server.start()

            val endpointClient = DefaultServerBootstrapEndpointClient(
                serverBootstrapUrl = server.url("/").toString()
            )

            val actualParams = endpointClient.execute(testCode)
            actualParams shouldBe (Success(testParams))

            val request = server.takeRequest()
            request.path shouldBe ("/v1/devices/execute")
            request.method shouldBe ("PUT")
            request.body.size shouldBe (0)
            request.headers[HttpCredentials.AuthorizationHeader] shouldBe ("Bearer $testCode")

            server.shutdown()
        }

        "handle boostrap request failures (unauthorized)" {
            val server = MockWebServer()
            server.enqueue(MockResponse().setResponseCode(401))
            server.start()

            val endpointClient = DefaultServerBootstrapEndpointClient(
                serverBootstrapUrl = server.url("/").toString()
            )

            when (val result = endpointClient.execute(bootstrapCode = "test-code")) {
                is Success -> fail("Unexpected successful result received: [$result]")
                is Failure -> result.exception should beInstanceOf<InvalidBootstrapCodeFailure>()
            }

            server.shutdown()
        }

        "handle boostrap request failures (server failures)" {
            val server = MockWebServer()
            server.enqueue(MockResponse().setResponseCode(500))
            server.start()

            val endpointClient = DefaultServerBootstrapEndpointClient(
                serverBootstrapUrl = server.url("/").toString()
            )

            when (val result = endpointClient.execute(bootstrapCode = "test-code")) {
                is Success -> fail("Unexpected successful result received: [$result]")
                is Failure -> {
                    result.exception should beInstanceOf<EndpointFailure>()
                    result.exception.message shouldContain ("responded with [500 - Server Error]")
                }
            }

            server.shutdown()
        }
    }
})
