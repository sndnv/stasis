package stasis.client_android.lib.security

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import stasis.client_android.lib.security.exceptions.ExplicitLogout
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Companion.flatMap
import stasis.client_android.lib.utils.Try.Companion.map
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.lib.utils.Try.Success
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

class OAuthTokenManager(
    private val oAuthClient: OAuthClient,
    private val onTokenUpdated: (Try<AccessTokenResponse>) -> Unit,
    private val expirationTolerance: Duration,
    private val initTimeout: Duration
) {
    private val storedTokenRef: AtomicReference<Try<StoredAccessTokenResponse>> =
        AtomicReference(Failure(RuntimeException("No access token found")))

    private val init: CompletableDeferred<Unit> = CompletableDeferred()

    suspend fun token(): Try<AccessTokenResponse> =
        retrieve().flatMap { stored ->
            if (stored.isValid(withTolerance = expirationTolerance)) {
                Success(stored.underlying)
            } else {
                if (stored.hasRefreshToken) {
                    refreshWithToken(stored.underlying.refresh_token!!, stored.underlying.scope)
                } else {
                    refreshWithClientCredentials(stored.underlying.scope)
                }
            }
        }

    fun configureWithRefreshToken(response: Try<AccessTokenResponse>) =
        configure(response, hasRefreshToken = true)

    fun configureWithClientCredentials(response: Try<AccessTokenResponse>) =
        configure(response, hasRefreshToken = false)

    fun reset() {
        store(Failure(ExplicitLogout()))
        onTokenUpdated(Failure(ExplicitLogout()))
    }

    private suspend fun retrieve(): Try<StoredAccessTokenResponse> {
        return if (withTimeoutOrNull(timeMillis = initTimeout.toMillis()) { init.await() } == null) {
            Failure(TimeoutException("OAuthTokenManager not initialized in time [$initTimeout]"))
        } else {
            storedTokenRef.get()
        }
    }

    private fun store(response: Try<StoredAccessTokenResponse>) {
        storedTokenRef.set(response)
        init.complete(Unit)
    }

    private fun configure(response: Try<AccessTokenResponse>, hasRefreshToken: Boolean) {
        store(response.map { StoredAccessTokenResponse(it, hasRefreshToken) })
        onTokenUpdated(response)
    }

    private suspend fun refreshWithToken(token: String, tokenScope: String?): Try<AccessTokenResponse> {
        val refreshedResponse = oAuthClient.token(
            scope = tokenScope,
            parameters = OAuthClient.GrantParameters.RefreshToken(token)
        )

        when (refreshedResponse) {
            is Success -> {
                val updatedResponse = refreshedResponse.value.copy(
                    refresh_token = refreshedResponse.value.refresh_token ?: token
                )

                store(Success(StoredAccessTokenResponse(updatedResponse, hasRefreshToken = true)))
                onTokenUpdated(Success(updatedResponse))

                return Success(updatedResponse)
            }

            is Failure -> {
                store(Failure(refreshedResponse.exception))

                return Failure(refreshedResponse.exception)
            }
        }
    }

    private suspend fun refreshWithClientCredentials(tokenScope: String?): Try<AccessTokenResponse> {
        val refreshedResponse = oAuthClient.token(
            scope = tokenScope,
            parameters = OAuthClient.GrantParameters.ClientCredentials
        )

        when (refreshedResponse) {
            is Success -> {
                store(
                    Success(
                        StoredAccessTokenResponse(
                            refreshedResponse.value,
                            hasRefreshToken = false
                        )
                    )
                )
                onTokenUpdated(refreshedResponse)
            }

            is Failure -> {
                store(Failure(refreshedResponse.exception))
            }
        }

        return refreshedResponse
    }

    companion object {
        private val DefaultInitTimeout: Duration = Duration.ofSeconds(5)

        operator fun invoke(
            oAuthClient: OAuthClient,
            onTokenUpdated: (Try<AccessTokenResponse>) -> Unit,
            expirationTolerance: Duration,
        ): OAuthTokenManager = OAuthTokenManager(
            oAuthClient = oAuthClient,
            onTokenUpdated = onTokenUpdated,
            expirationTolerance = expirationTolerance,
            initTimeout = DefaultInitTimeout
        )

        private data class StoredAccessTokenResponse(
            val underlying: AccessTokenResponse,
            val expiresAt: Instant,
            val hasRefreshToken: Boolean
        ) {
            fun isValid(withTolerance: Duration): Boolean =
                expiresAt.isAfter(Instant.now().plusMillis(withTolerance.toMillis()))

            companion object {
                operator fun invoke(
                    response: AccessTokenResponse,
                    hasRefreshToken: Boolean
                ): StoredAccessTokenResponse =
                    StoredAccessTokenResponse(
                        underlying = response,
                        expiresAt = Instant.now().plusSeconds(response.expires_in),
                        hasRefreshToken = hasRefreshToken
                    )
            }
        }
    }
}
