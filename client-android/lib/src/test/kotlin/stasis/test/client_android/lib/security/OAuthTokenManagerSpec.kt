package stasis.test.client_android.lib.security

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf
import stasis.client_android.lib.security.AccessTokenResponse
import stasis.client_android.lib.security.OAuthClient
import stasis.client_android.lib.security.OAuthTokenManager
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.lib.utils.Try.Success
import stasis.test.client_android.lib.eventually
import java.time.Duration
import java.util.Collections
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

class OAuthTokenManagerSpec : WordSpec({
    "An OAuthTokenManager" should {
        val expiration = Duration.ofSeconds(1)
        val tolerance = Duration.ofMillis(100)

        val response = AccessTokenResponse(
            access_token = "test-token",
            refresh_token = null,
            expires_in = expiration.toSeconds(),
            scope = null
        )

        "provide the latest available token (with refresh token)" {
            val requested = AtomicInteger(0)
            val updates = Collections.synchronizedList(mutableListOf<Try<AccessTokenResponse>>())

            val refreshToken = "refresh-token"
            val expectedResponse = response.copy(refresh_token = refreshToken)

            val client = object : OAuthClient {
                override suspend fun token(
                    scope: String?,
                    parameters: OAuthClient.GrantParameters
                ): Try<AccessTokenResponse> {
                    requested.incrementAndGet()
                    return Success(expectedResponse)
                }
            }

            val manager = OAuthTokenManager(
                oAuthClient = client,
                onTokenUpdated = { updates.add(it) },
                expirationTolerance = tolerance,
                initTimeout = Duration.ofMillis(250)
            )

            manager.token().failed().get() should beInstanceOf<TimeoutException>() // not initialized
            manager.configureWithRefreshToken(Success(expectedResponse))
            manager.token() shouldBe (Success(expectedResponse))

            eventually {
                requested.get() shouldBe (0)
                updates.size shouldBe (1)
                updates[0] shouldBe (Success(expectedResponse))
            }

            manager.reset()
        }

        "provide the latest available token (with client credentials)" {
            val requested = AtomicInteger(0)
            val updates = Collections.synchronizedList(mutableListOf<Try<AccessTokenResponse>>())

            val client = object : OAuthClient {
                override suspend fun token(
                    scope: String?,
                    parameters: OAuthClient.GrantParameters
                ): Try<AccessTokenResponse> {
                    requested.incrementAndGet()
                    return Success(response)
                }
            }

            val manager = OAuthTokenManager(
                oAuthClient = client,
                onTokenUpdated = { updates.add(it) },
                expirationTolerance = tolerance,
                initTimeout = Duration.ofMillis(250)
            )

            manager.token().failed().get() should beInstanceOf<TimeoutException>() // not initialized
            manager.configureWithClientCredentials(Success(response))
            manager.token() shouldBe (Success(response))

            eventually {
                requested.get() shouldBe (0)
                updates.size shouldBe (1)
                updates[0] shouldBe (Success(response))
            }

            manager.reset()
        }

        "retrieve new tokens when old ones expire (with refresh token)" {
            val requested = AtomicInteger(0)
            val updates = Collections.synchronizedList(mutableListOf<Try<AccessTokenResponse>>())

            val refreshToken = "refresh-token"
            val expiredResponse = response.copy(refresh_token = refreshToken, expires_in = 0)
            val validResponse = response.copy(refresh_token = refreshToken, expires_in = 42)

            val client = object : OAuthClient {
                override suspend fun token(
                    scope: String?,
                    parameters: OAuthClient.GrantParameters
                ): Try<AccessTokenResponse> {
                    requested.incrementAndGet()
                    return Success(validResponse)
                }
            }

            val manager = OAuthTokenManager(
                oAuthClient = client,
                onTokenUpdated = { updates.add(it) },
                expirationTolerance = tolerance,
                initTimeout = Duration.ofMillis(250)
            )

            manager.token().failed().get() should beInstanceOf<TimeoutException>() // not initialized
            manager.configureWithRefreshToken(Success(expiredResponse))
            manager.token() shouldBe (Success(validResponse))

            eventually {
                requested.get() shouldBe (1)
                updates.size shouldBe (2)
                updates[0] shouldBe (Success(expiredResponse))
                updates[1] shouldBe (Success(validResponse))
            }

            manager.token() shouldBe (Success(validResponse))

            manager.reset()
        }

        "retrieve new tokens when old ones expire (with client credentials)" {
            val requested = AtomicInteger(0)
            val updates = Collections.synchronizedList(mutableListOf<Try<AccessTokenResponse>>())

            val expiredResponse = response.copy(expires_in = 0)
            val validResponse = response.copy(expires_in = 42)

            val client = object : OAuthClient {
                override suspend fun token(
                    scope: String?,
                    parameters: OAuthClient.GrantParameters
                ): Try<AccessTokenResponse> {
                    requested.incrementAndGet()
                    return Success(validResponse)
                }
            }

            val manager = OAuthTokenManager(
                oAuthClient = client,
                onTokenUpdated = { updates.add(it) },
                expirationTolerance = tolerance,
                initTimeout = Duration.ofMillis(250)
            )

            manager.token().failed().get() should beInstanceOf<TimeoutException>() // not initialized
            manager.configureWithClientCredentials(Success(expiredResponse))
            manager.token() shouldBe (Success(validResponse))

            eventually {
                requested.get() shouldBe (1)
                updates.size shouldBe (2)
                updates[0] shouldBe (Success(expiredResponse))
                updates[1] shouldBe (Success(validResponse))
            }

            manager.token() shouldBe (Success(validResponse))

            manager.reset()
        }

        "fail if a valid token could not be retrieved (with refresh token)" {
            val requested = AtomicInteger(0)
            val updates = Collections.synchronizedList(mutableListOf<Try<AccessTokenResponse>>())

            val refreshToken = "refresh-token"
            val expiredResponse = response.copy(refresh_token = refreshToken, expires_in = 0)

            val client = object : OAuthClient {
                override suspend fun token(
                    scope: String?,
                    parameters: OAuthClient.GrantParameters
                ): Try<AccessTokenResponse> {
                    requested.incrementAndGet()
                    return Failure(RuntimeException("Test failure"))
                }
            }

            val manager = OAuthTokenManager(
                oAuthClient = client,
                onTokenUpdated = { updates.add(it) },
                expirationTolerance = tolerance,
                initTimeout = Duration.ofMillis(250)
            )

            manager.token().failed().get() should beInstanceOf<TimeoutException>() // not initialized
            manager.configureWithRefreshToken(Success(expiredResponse))
            manager.token().failed().get().message shouldBe ("Test failure")

            eventually {
                requested.get() shouldBe (1)
                updates.size shouldBe (1)
                updates[0] shouldBe (Success(expiredResponse))
            }

            manager.token().failed().get().message shouldBe ("Test failure")

            manager.reset()
        }

        "fail if a valid token could not be retrieved (with client credentials)" {
            val requested = AtomicInteger(0)
            val updates = Collections.synchronizedList(mutableListOf<Try<AccessTokenResponse>>())

            val expiredResponse = response.copy(expires_in = 0)

            val client = object : OAuthClient {
                override suspend fun token(
                    scope: String?,
                    parameters: OAuthClient.GrantParameters
                ): Try<AccessTokenResponse> {
                    requested.incrementAndGet()
                    return Failure(RuntimeException("Test failure"))
                }
            }

            val manager = OAuthTokenManager(
                oAuthClient = client,
                onTokenUpdated = { updates.add(it) },
                expirationTolerance = tolerance,
                initTimeout = Duration.ofMillis(250)
            )

            manager.token().failed().get() should beInstanceOf<TimeoutException>() // not initialized
            manager.configureWithClientCredentials(Success(expiredResponse))
            manager.token().failed().get().message shouldBe ("Test failure")

            eventually {
                requested.get() shouldBe (1)
                updates.size shouldBe (1)
                updates[0] shouldBe (Success(expiredResponse))
            }

            manager.token().failed().get().message shouldBe ("Test failure")

            manager.reset()
        }
    }
})
