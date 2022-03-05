package stasis.test.client_android.lib.security

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okhttp3.Credentials
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import stasis.client_android.lib.api.clients.exceptions.AccessDeniedFailure
import stasis.client_android.lib.api.clients.exceptions.EndpointFailure
import stasis.client_android.lib.security.AccessTokenResponse
import stasis.client_android.lib.security.DefaultOAuthClient
import stasis.client_android.lib.security.HttpCredentials
import stasis.client_android.lib.security.OAuthClient
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.lib.utils.Try.Success

class DefaultOAuthClientSpec : WordSpec({
    "A DefaultOAuthClient" should {
        val credentials = HttpCredentials.BasicHttpCredentials(
            username = "test-client",
            password = "test-client-password"
        )
        val credentialsHeader = Credentials.basic(credentials.username, credentials.password)

        val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

        val expectedResponse = AccessTokenResponse(
            access_token = "test-token",
            refresh_token = "test-refresh-token",
            expires_in = 42,
            scope = "test-scope"
        )

        val expectedResponseData =
            moshi.adapter(AccessTokenResponse::class.java).toJson(expectedResponse)

        "successfully retrieve tokens (client credentials)" {
            val server = MockWebServer()
            server.enqueue(MockResponse().setBody(expectedResponseData))
            server.start()

            val client = DefaultOAuthClient(
                tokenEndpoint = server.url("/").toString(),
                client = credentials.username,
                clientSecret = credentials.password
            )

            val actualResponse = client.token(
                scope = "test-scope",
                parameters = OAuthClient.GrantParameters.ClientCredentials
            )

            actualResponse shouldBe (Success(expectedResponse))

            val request = server.takeRequest()
            request.path shouldBe ("/")
            request.method shouldBe ("POST")
            request.body.readUtf8() shouldBe ("scope=${expectedResponse.scope}&grant_type=client_credentials")
            request.headers[HttpCredentials.AuthorizationHeader] shouldBe (credentialsHeader)

            server.shutdown()
        }

        "successfully retrieve tokens (resource owner password credentials)" {
            val server = MockWebServer()
            server.enqueue(MockResponse().setBody(expectedResponseData))
            server.start()

            val client = DefaultOAuthClient(
                tokenEndpoint = server.url("/").toString(),
                client = credentials.username,
                clientSecret = credentials.password
            )

            val user = "test-user"
            val userPassword = "test-user-password"

            val actualResponse = client.token(
                scope = "test-scope",
                parameters = OAuthClient.GrantParameters.ResourceOwnerPasswordCredentials(
                    username = user,
                    password = userPassword
                )
            )

            actualResponse shouldBe (Success(expectedResponse))

            val request = server.takeRequest()
            request.path shouldBe ("/")
            request.method shouldBe ("POST")
            request.body.readUtf8() shouldBe (
                    "scope=${expectedResponse.scope}&grant_type=password&username=$user&password=$userPassword"
                    )
            request.headers[HttpCredentials.AuthorizationHeader] shouldBe (credentialsHeader)

            server.shutdown()
        }

        "successfully retrieve tokens (refresh)" {
            val server = MockWebServer()
            server.enqueue(MockResponse().setBody(expectedResponseData))
            server.start()

            val client = DefaultOAuthClient(
                tokenEndpoint = server.url("/").toString(),
                client = credentials.username,
                clientSecret = credentials.password
            )

            val refreshToken = "test-token"

            val actualResponse = client.token(
                scope = "test-scope",
                parameters = OAuthClient.GrantParameters.RefreshToken(
                    refreshToken = refreshToken
                )
            )

            actualResponse shouldBe (Success(expectedResponse))

            val request = server.takeRequest()
            request.path shouldBe ("/")
            request.method shouldBe ("POST")
            request.body.readUtf8() shouldBe (
                    "scope=${expectedResponse.scope}&grant_type=refresh_token&refresh_token=$refreshToken"
                    )
            request.headers[HttpCredentials.AuthorizationHeader] shouldBe (credentialsHeader)

            server.shutdown()
        }

        "support providing no scope" {
            val server = MockWebServer()
            server.enqueue(MockResponse().setBody(expectedResponseData))
            server.start()

            val client = DefaultOAuthClient(
                tokenEndpoint = server.url("/").toString(),
                client = credentials.username,
                clientSecret = credentials.password
            )

            val actualResponse = client.token(
                scope = null,
                parameters = OAuthClient.GrantParameters.ClientCredentials
            )

            actualResponse shouldBe (Success(expectedResponse))

            val request = server.takeRequest()
            request.path shouldBe ("/")
            request.method shouldBe ("POST")
            request.body.readUtf8() shouldBe ("grant_type=client_credentials")
            request.headers[HttpCredentials.AuthorizationHeader] shouldBe (credentialsHeader)

            server.shutdown()
        }

        "support handling unauthorized responses" {
            val server = MockWebServer()
            server.enqueue(MockResponse().setResponseCode(DefaultOAuthClient.StatusUnauthorized))
            server.start()

            val client = DefaultOAuthClient(
                tokenEndpoint = server.url("/").toString(),
                client = credentials.username,
                clientSecret = credentials.password
            )

            val actualResponse = client.token(
                scope = null,
                parameters = OAuthClient.GrantParameters.ClientCredentials
            )

            shouldThrow<AccessDeniedFailure> { actualResponse.get() }

            val request = server.takeRequest()
            request.path shouldBe ("/")
            request.method shouldBe ("POST")
            request.body.readUtf8() shouldBe ("grant_type=client_credentials")
            request.headers[HttpCredentials.AuthorizationHeader] shouldBe (credentialsHeader)

            server.shutdown()
        }

        "support handling failed responses" {
            val server = MockWebServer()
            server.enqueue(MockResponse().setResponseCode(500))
            server.start()

            val client = DefaultOAuthClient(
                tokenEndpoint = server.url("/").toString(),
                client = credentials.username,
                clientSecret = credentials.password
            )

            val actualResponse = client.token(
                scope = null,
                parameters = OAuthClient.GrantParameters.ClientCredentials
            )

            val e = shouldThrow<EndpointFailure> { actualResponse.get() }
            e.message shouldBe ("Server responded with [500 - Server Error]")

            val request = server.takeRequest()
            request.path shouldBe ("/")
            request.method shouldBe ("POST")
            request.body.readUtf8() shouldBe ("grant_type=client_credentials")
            request.headers[HttpCredentials.AuthorizationHeader] shouldBe (credentialsHeader)

            server.shutdown()
        }
    }
})
