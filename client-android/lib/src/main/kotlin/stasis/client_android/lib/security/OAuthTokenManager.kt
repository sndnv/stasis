package stasis.client_android.lib.security

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import stasis.client_android.lib.security.exceptions.ExplicitLogout
import stasis.client_android.lib.security.exceptions.TokenExpired
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Companion.foreach
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.lib.utils.Try.Success
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

class OAuthTokenManager(
    private val oAuthClient: OAuthClient,
    private val onTokenUpdated: (Try<AccessTokenResponse>) -> Unit,
    private val expirationTolerance: Duration,
    private val coroutineScope: CoroutineScope
) {
    private val jobRef: AtomicReference<Job?> = AtomicReference(null)

    fun scheduleWithRefreshToken(
        response: Try<AccessTokenResponse>
    ) {
        response.foreach { scheduleWithRefreshToken(it) }
        onTokenUpdated(response)
    }

    fun scheduleWithClientCredentials(
        response: Try<AccessTokenResponse>
    ) {
        response.foreach { scheduleWithClientCredentials(it) }
        onTokenUpdated(response)
    }

    private fun scheduleWithClientCredentials(
        response: AccessTokenResponse
    ) {
        val job = refreshWithClientCredentials(
            tokenScope = response.scope,
            after = Duration.ofSeconds(response.expires_in) - expirationTolerance
        )

        replaceJob(job)
    }

    private fun scheduleWithRefreshToken(
        response: AccessTokenResponse
    ) {
        val job = when (val refreshToken = response.refresh_token) {
            null -> expire(
                after = Duration.ofSeconds(response.expires_in)
            )

            else -> refreshWithToken(
                token = refreshToken,
                tokenScope = response.scope,
                after = Duration.ofSeconds(response.expires_in) - expirationTolerance
            )
        }

        replaceJob(job)
    }

    fun reset() {
        jobRef.getAndSet(null)?.let { job ->
            job.cancel()
            onTokenUpdated(Failure(ExplicitLogout()))
        }
    }

    private fun expire(after: Duration): Job {
        return coroutineScope.launch {
            delay(timeMillis = after.toMillis())
            onTokenUpdated(Failure(TokenExpired()))
        }
    }

    private fun refreshWithClientCredentials(
        tokenScope: String?,
        after: Duration
    ): Job {
        return coroutineScope.launch {
            delay(timeMillis = after.toMillis())

            val refreshedResponse = oAuthClient.token(
                scope = tokenScope,
                parameters = OAuthClient.GrantParameters.ClientCredentials
            )

            when (refreshedResponse) {
                is Success -> {
                    scheduleWithClientCredentials(refreshedResponse.value)
                    onTokenUpdated(refreshedResponse)
                }

                is Failure -> replaceJob(expire(after = expirationTolerance))
            }
        }
    }

    private fun refreshWithToken(token: String, tokenScope: String?, after: Duration): Job {
        return coroutineScope.launch {
            delay(timeMillis = after.toMillis())

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
                    onTokenUpdated(Success(updatedResponse))
                }

                is Failure -> replaceJob(expire(after = expirationTolerance))
            }
        }
    }

    private fun replaceJob(job: Job?) {
        jobRef.getAndSet(job)?.cancel()
    }
}
