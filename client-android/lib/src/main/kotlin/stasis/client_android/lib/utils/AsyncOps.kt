package stasis.client_android.lib.utils

import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import java.time.Duration
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object AsyncOps {
    suspend fun Call.async(): Response = suspendCancellableCoroutine { continuation ->
        val callback = object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }
        }

        enqueue(callback)
        continuation.invokeOnCancellation { cancel() }
    }

    suspend fun Call.asyncRetryWith(
        config: RetryConfig,
    ): Response = (0 until config.maxRetries).fold(config.minBackoff to async()) { current, _ ->
        val (latestDelay, latestResponse) = current
        if (canRetry(latestResponse.code)) {
            val currentDelay = (latestDelay * (1 + config.randomFactor)).toLong().coerceAtMost(config.maxBackoff)
            delay(timeMillis = currentDelay)
            currentDelay to clone().async()
        } else {
            return latestResponse
        }
    }.second

    @Suppress("MagicNumber")
    fun canRetry(status: Int): Boolean =
        when (status) {
            408 /* RequestTimeout */ -> true
            424 /* FailedDependency */ -> true
            425 /* TooEarly */ -> true
            429 /* TooManyRequests */ -> true

            500 /* InternalServerError */ -> true
            502 /* BadGateway */ -> true
            503 /* ServiceUnavailable */ -> true
            504 /* GatewayTimeout */ -> true
            509 /* BandwidthLimitExceeded */ -> true
            598 /* NetworkReadTimeout */ -> true
            599 /* NetworkConnectTimeout */ -> true

            else -> false
        }

    data class RetryConfig(
        val minBackoff: Long,
        val maxBackoff: Long,
        val randomFactor: Double,
        val maxRetries: Int,
    ) {
        companion object {
            operator fun invoke(
                minBackoff: Duration,
                maxBackoff: Duration,
                randomFactor: Double,
                maxRetries: Int
            ): RetryConfig = RetryConfig(
                minBackoff = minBackoff.toMillis(),
                maxBackoff = maxBackoff.toMillis(),
                randomFactor = randomFactor,
                maxRetries = maxRetries
            )

            val Default: RetryConfig = RetryConfig(
                minBackoff = Duration.ofMillis(500),
                maxBackoff = Duration.ofSeconds(3),
                randomFactor = 0.1,
                maxRetries = 5
            )
        }
    }
}
