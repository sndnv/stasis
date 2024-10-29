package stasis.test.client_android.lib.security

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.security.AccessTokenResponse
import stasis.client_android.lib.security.OAuthClient
import stasis.client_android.lib.security.OAuthTokenManager
import stasis.client_android.lib.security.exceptions.TokenExpired
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.lib.utils.Try.Success
import stasis.test.client_android.lib.eventually
import java.time.Duration
import java.util.*
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

        "schedule expiration of access tokens (refresh token grant without refresh tokens)" {
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
                coroutineScope = testScope
            )

            manager.scheduleWithRefreshToken(Success(response))

            eventually {
                requested.get() shouldBe (0)
                updates.size shouldBe (2)

                updates[0] shouldBe (Success(response))
                shouldThrow<TokenExpired> { updates[1].get() }
            }

            manager.reset()
        }

        "schedule refresh of access tokens (refresh token grant with refresh tokens)" {
            val requested = AtomicInteger(0)
            val updates = Collections.synchronizedList(mutableListOf<Try<AccessTokenResponse>>())

            val refreshToken = "refresh-token"

            val client = object : OAuthClient {
                override suspend fun token(
                    scope: String?,
                    parameters: OAuthClient.GrantParameters
                ): Try<AccessTokenResponse> {
                    requested.incrementAndGet()
                    return Success(response.copy(refresh_token = refreshToken))
                }
            }

            val manager = OAuthTokenManager(
                oAuthClient = client,
                onTokenUpdated = { updates.add(it) },
                expirationTolerance = tolerance,
                coroutineScope = testScope
            )

            val expectedResponse = response.copy(refresh_token = refreshToken)

            manager.scheduleWithRefreshToken(Success(response.copy(refresh_token = refreshToken)))

            eventually {
                requested.get() shouldBe (2)
                updates.size shouldBe (3)

                updates shouldBe (listOf(
                    Success(expectedResponse),
                    Success(expectedResponse),
                    Success(expectedResponse)
                ))
            }

            manager.reset()
        }

        "schedule expiration of access tokens (refresh token grant when token refresh fails)" {
            val requested = AtomicInteger(0)
            val updates = Collections.synchronizedList(mutableListOf<Try<AccessTokenResponse>>())

            val refreshToken = "refresh-token"

            val client = object : OAuthClient {
                override suspend fun token(
                    scope: String?,
                    parameters: OAuthClient.GrantParameters
                ): Try<AccessTokenResponse> {
                    return if (requested.incrementAndGet() < 2) {
                        Success(response.copy(refresh_token = refreshToken))
                    } else {
                        Failure(RuntimeException("Test failure"))
                    }
                }
            }

            val manager = OAuthTokenManager(
                oAuthClient = client,
                onTokenUpdated = { updates.add(it) },
                expirationTolerance = tolerance,
                coroutineScope = testScope
            )

            val expectedResponse = response.copy(refresh_token = refreshToken)

            manager.scheduleWithRefreshToken(Success(expectedResponse))

            eventually {
                requested.get() shouldBe (2)
                updates.size shouldBe (3)

                updates[0] shouldBe (Success(expectedResponse))
                updates[1] shouldBe (Success(expectedResponse))
                shouldThrow<TokenExpired> { updates[2].get() }
            }

            manager.reset()
        }

        "schedule refresh of access tokens (client_credentials token grant with refresh tokens)" {
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
                coroutineScope = testScope
            )

            manager.scheduleWithClientCredentials(Success(response))

            eventually {
                requested.get() shouldBe (2)
                updates.size shouldBe (3)

                updates shouldBe (listOf(
                    Success(response),
                    Success(response),
                    Success(response)
                ))
            }

            manager.reset()
        }

        "schedule expiration of access tokens (client_credentials token grant when token refresh fails)" {
            val requested = AtomicInteger(0)
            val updates = Collections.synchronizedList(mutableListOf<Try<AccessTokenResponse>>())

            val client = object : OAuthClient {
                override suspend fun token(
                    scope: String?,
                    parameters: OAuthClient.GrantParameters
                ): Try<AccessTokenResponse> {
                    return if (requested.incrementAndGet() < 2) {
                        Success(response)
                    } else {
                        Failure(RuntimeException("Test failure"))
                    }
                }
            }

            val manager = OAuthTokenManager(
                oAuthClient = client,
                onTokenUpdated = { updates.add(it) },
                expirationTolerance = tolerance,
                coroutineScope = testScope
            )

            manager.scheduleWithClientCredentials(Success(response))

            eventually {
                requested.get() shouldBe (2)
                updates.size shouldBe (3)

                updates[0] shouldBe (Success(response))
                updates[1] shouldBe (Success(response))
                shouldThrow<TokenExpired> { updates[2].get() }
            }

            manager.reset()
        }

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
                coroutineScope = testScope
            )

            manager.token.failed().get().message shouldBe ("No access token found")
            manager.scheduleWithRefreshToken(Success(expectedResponse))
            manager.token shouldBe (Success(expectedResponse))

            requested.get() shouldBe (0)
            updates.size shouldBe (1)
            updates[0] shouldBe (Success(expectedResponse))

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
                coroutineScope = testScope
            )

            manager.token.failed().get().message shouldBe ("No access token found")
            manager.scheduleWithClientCredentials(Success(response))
            manager.token shouldBe (Success(response))

            requested.get() shouldBe (0)
            updates.size shouldBe (1)
            updates[0] shouldBe (Success(response))

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
                coroutineScope = testScope
            )

            manager.token.failed().get().message shouldBe ("No access token found")

            manager.scheduleWithRefreshToken(Success(expiredResponse))

            manager.token shouldBe (Success(validResponse))

            requested.get() shouldBe (1)
            updates.size shouldBe (2)
            updates[0] shouldBe (Success(expiredResponse))
            updates[1] shouldBe (Success(validResponse))

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
                coroutineScope = testScope
            )

            manager.token.failed().get().message shouldBe ("No access token found")

            manager.scheduleWithClientCredentials(Success(expiredResponse))

            manager.token shouldBe (Success(validResponse))

            requested.get() shouldBe (1)
            updates.size shouldBe (2)
            updates[0] shouldBe (Success(expiredResponse))
            updates[1] shouldBe (Success(validResponse))

            manager.reset()
        }

        "fail if a valid token could not be retrieved" {
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
                coroutineScope = testScope
            )

            manager.token.failed().get().message shouldBe ("No access token found")

            manager.scheduleWithClientCredentials(Success(expiredResponse))

            manager.token.failed().get().message shouldBe ("Test failure")

            requested.get() shouldBe (1)
            updates.size shouldBe (1)
            updates[0] shouldBe (Success(expiredResponse))

            manager.reset()
        }
    }
})
