package stasis.test.client_android.lib.api.clients.internal

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.Source
import stasis.client_android.lib.security.HttpCredentials

class ClientExtensionsSpec : WordSpec({
    "ClientExtensions" should {
        "convert model classes to request data" {
            val client = TestClient()

            val data = TestDataClass(int = 42, bool = true, string = "test")
            val buffer = Buffer()

            client.write(data = data, to = buffer)

            buffer.readUtf8() shouldBe ("""{"int":42,"bool":true,"string":"test"}""")
        }

        "convert byte streams to request data" {
            val client = TestClient()

            val data: Source = Buffer().write("test".toByteArray())
            val buffer = Buffer()

            client.write(data = data, to = buffer)

            buffer.readUtf8() shouldBe ("test")
        }

        "convert response data to model classes" {
            val client = TestClient()

            val data = """{"int":1,"bool":false,"string":"other"}"""

            client.read() shouldBe (null)
            client.read(from = data) shouldBe (TestDataClass(int = 1, bool = false, string = "other"))
        }

        "convert response data to lists of model classes" {
            val client = TestClient()

            val data = """[
                |{"int":1,"bool":false,"string":"other"},
                |{"int":2,"bool":true,"string":"other"},
                |{"int":3,"bool":false,"string":"other"}
                |]""".trimMargin()

            client.readList() shouldBe (null)
            client.readList(from = data) shouldBe (
                    listOf(
                        TestDataClass(int = 1, bool = false, string = "other"),
                        TestDataClass(int = 2, bool = true, string = "other"),
                        TestDataClass(int = 3, bool = false, string = "other"),
                    )
                    )
        }

        "convert response data to required model classes" {
            val client = TestClient()

            val data = """{"int":-1,"bool":true,"string":"test-123"}"""

            val e = shouldThrow<IllegalArgumentException> { client.readRequired() }
            e.message shouldBe ("Expected data but none was found")

            client.readRequired(from = data) shouldBe (TestDataClass(int = -1, bool = true, string = "test-123"))
        }

        "convert response data to required lists of model classes" {
            val client = TestClient()

            val data = """[
                |{"int":-1,"bool":true,"string":"test-123"},
                |{"int":-2,"bool":false,"string":"test-456"},
                |{"int":-3,"bool":true,"string":"test-789"}
                |]""".trimMargin()

            val e = shouldThrow<IllegalArgumentException> { client.readRequiredList() }
            e.message shouldBe ("Expected data but none was found")

            client.readRequiredList(from = data) shouldBe (
                    listOf(
                        TestDataClass(int = -1, bool = true, string = "test-123"),
                        TestDataClass(int = -2, bool = false, string = "test-456"),
                        TestDataClass(int = -3, bool = true, string = "test-789")
                    )
                    )
        }

        "support making requests with credentials" {
            val expectedResponse = "test-response"
            val expectedToken = "test-token"

            val server = MockWebServer()
            server.enqueue(MockResponse().setBody(expectedResponse))
            server.start()

            val client = TestClient(providedCredentials = HttpCredentials.OAuth2BearerToken(token = expectedToken))

            val response = client.makeRequest(server.url("/test").toString())
            response.body?.string() shouldBe (expectedResponse)

            val request = server.takeRequest()
            request.headers[HttpCredentials.AuthorizationHeader] shouldBe ("Bearer $expectedToken")

            server.shutdown()
        }

        "support making JSON requests with credentials" {
            val client = TestClient(providedCredentials = HttpCredentials.None)

            val server = MockWebServer()
            server.start()

            server.enqueue(MockResponse().setBody("""{"int":42,"bool":false,"string":"other-string"}"""))

            val response = client.makeJsonRequest(server.url("/test").toString())
            response shouldBe (TestDataClass(int = 42, bool = false, string = "other-string"))

            server.enqueue(
                MockResponse().setBody(
                    """[{"int":42,"bool":false,"string":"some-string"},{"int":84,"bool":true,"string":"other-string"}]""".trimMargin()
                )
            )

            val responseList = client.makeJsonListRequest(server.url("/test").toString())
            responseList shouldBe (
                    listOf(
                        TestDataClass(int = 42, bool = false, string = "some-string"),
                        TestDataClass(int = 84, bool = true, string = "other-string")
                    )
                    )

            val request = server.takeRequest()
            request.headers[HttpCredentials.AuthorizationHeader] shouldBe (null)

            server.shutdown()
        }
    }
})
