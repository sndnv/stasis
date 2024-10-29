package stasis.client_android.lib.security

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import stasis.client_android.lib.security.exceptions.ExplicitLogout
import stasis.client_android.lib.security.exceptions.TokenExpired
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Companion.flatMap
import stasis.client_android.lib.utils.Try.Companion.foreach
import stasis.client_android.lib.utils.Try.Companion.map
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.lib.utils.Try.Success
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

class OAuthTokenManager(
    private val oAuthClient: OAuthClient,
    private val onTokenUpdated: (Try<AccessTokenResponse>) -> Unit,
    private val expirationTolerance: Duration,
    private val coroutineScope: CoroutineScope
) {
    private val jobRef: AtomicReference<Job?> = AtomicReference(null)

    private val storedTokenRef: AtomicReference<Try<StoredAccessTokenResponse>> =
        AtomicReference(Failure(RuntimeException("No access token found")))

    val token: Try<AccessTokenResponse>
        get() = storedTokenRef.get().flatMap { stored ->
            if (stored.isValid(withTolerance = expirationTolerance)) {
                Success(stored.underlying)
            } else {
                runBlocking {
                    if (stored.hasRefreshToken) {
                        refreshWithToken(stored.underlying.refresh_token!!, stored.underlying.scope)
                    } else {
                        refreshWithClientCredentials(stored.underlying.scope)
                    }
                }
            }
        }

    fun scheduleWithRefreshToken(response: Try<AccessTokenResponse>) =
        schedule(response, hasRefreshToken = true)

    fun scheduleWithClientCredentials(response: Try<AccessTokenResponse>) =
        schedule(response, hasRefreshToken = false)

    private fun schedule(response: Try<AccessTokenResponse>, hasRefreshToken: Boolean) {
        response.foreach {
            when {
                hasRefreshToken -> scheduleWithRefreshToken(it)
                else -> scheduleWithClientCredentials(it)
            }
        }

        storedTokenRef.set(response.map { StoredAccessTokenResponse(it, hasRefreshToken) })
        onTokenUpdated(response)
    }

    private fun scheduleWithClientCredentials(
        response: AccessTokenResponse
    ) {
        val job = coroutineScope.launch {
            delay(timeMillis = (Duration.ofSeconds(response.expires_in) - expirationTolerance).toMillis())
            refreshWithClientCredentials(response.scope)
        }

        replaceJob(job)
    }

    private fun scheduleWithRefreshToken(response: AccessTokenResponse) {
        val job = when (val refreshToken = response.refresh_token) {
            null -> expire(
                after = Duration.ofSeconds(response.expires_in)
            )

            else -> coroutineScope.launch {
                delay(timeMillis = (Duration.ofSeconds(response.expires_in) - expirationTolerance).toMillis())
                refreshWithToken(refreshToken, response.scope)
            }
        }

        replaceJob(job)
    }

    fun reset() {
        jobRef.getAndSet(null)?.let { job ->
            job.cancel()
            storedTokenRef.set(Failure(ExplicitLogout()))
            onTokenUpdated(Failure(ExplicitLogout()))
        }
    }

    private fun expire(after: Duration): Job {
        return coroutineScope.launch {
            delay(timeMillis = after.toMillis())
            storedTokenRef.set(Failure(TokenExpired()))
            onTokenUpdated(Failure(TokenExpired()))
        }
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

                scheduleWithRefreshToken(updatedResponse)
                storedTokenRef.set(Success(StoredAccessTokenResponse(updatedResponse, hasRefreshToken = true)))
                onTokenUpdated(Success(updatedResponse))

                return Success(updatedResponse)
            }

            is Failure -> {
                storedTokenRef.set(Failure(refreshedResponse.exception))
                replaceJob(expire(after = expirationTolerance))

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
                scheduleWithClientCredentials(refreshedResponse.value)
                storedTokenRef.set(
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
                storedTokenRef.set(Failure(refreshedResponse.exception))
                replaceJob(expire(after = expirationTolerance))
            }
        }

        return refreshedResponse
    }

    private fun replaceJob(job: Job?) {
        jobRef.getAndSet(job)?.cancel()
    }

    companion object {
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
