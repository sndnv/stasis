package stasis.test.client_android.lib.utils

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import stasis.client_android.lib.utils.AsyncOps
import stasis.client_android.lib.utils.AsyncOps.async
import stasis.client_android.lib.utils.AsyncOps.asyncRetryWith
import java.time.Duration

class AsyncOpsSpec : WordSpec({
    "AsyncOps" should {
        "support async calls" {
            val server = MockWebServer()
            server.enqueue(MockResponse().setBody("test"))
            server.start()

            val request = Request.Builder().url(server.url("/test")).build()

            val client = OkHttpClient()

            client.newCall(request).async().body?.string() shouldBe ("test")

            server.shutdown()
        }

        "support retrying calls" {
            val server = MockWebServer()
            server.enqueue(MockResponse().setResponseCode(408))
            server.enqueue(MockResponse().setResponseCode(425))
            server.enqueue(MockResponse().setResponseCode(429))
            server.enqueue(MockResponse().setResponseCode(500))
            server.enqueue(MockResponse().setResponseCode(502))
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(MockResponse().setResponseCode(200).setBody("test-response"))
            server.start()

            val request = Request.Builder()
                .url(server.url("/test"))
                .post(body = "test-request".toRequestBody())
                .build()

            val client = OkHttpClient.Builder().apply { retryOnConnectionFailure(false) }.build()

            val result = client.newCall(request).asyncRetryWith(
                config = AsyncOps.RetryConfig(
                    minBackoff = Duration.ofMillis(10),
                    maxBackoff = Duration.ofMillis(100),
                    randomFactor = 0.1,
                    maxRetries = 5
                )
            )

            result.code shouldBe (503)

            server.shutdown()
        }

        "support checking for retryable requests/responses" {
            // 1xx
            AsyncOps.canRetry(status = 100 /* Continue */) shouldBe (false)

            // 2xx
            AsyncOps.canRetry(status = 200 /* OK */) shouldBe (false)
            AsyncOps.canRetry(status = 201 /* Created */) shouldBe (false)
            AsyncOps.canRetry(status = 202 /* Accepted */) shouldBe (false)
            AsyncOps.canRetry(status = 204 /* NoContent */) shouldBe (false)

            // 3xx
            AsyncOps.canRetry(status = 302 /* Found */) shouldBe (false)
            AsyncOps.canRetry(status = 307 /* TemporaryRedirect */) shouldBe (false)
            AsyncOps.canRetry(status = 308 /* PermanentRedirect */) shouldBe (false)

            // 4xx
            AsyncOps.canRetry(status = 400 /* BadRequest */) shouldBe (false)
            AsyncOps.canRetry(status = 401 /* Unauthorized */) shouldBe (false)
            AsyncOps.canRetry(status = 403 /* Forbidden */) shouldBe (false)
            AsyncOps.canRetry(status = 404 /* NotFound */) shouldBe (false)
            AsyncOps.canRetry(status = 405 /* MethodNotAllowed */) shouldBe (false)
            AsyncOps.canRetry(status = 406 /* NotAcceptable */) shouldBe (false)
            AsyncOps.canRetry(status = 408 /* RequestTimeout */) shouldBe (true)
            AsyncOps.canRetry(status = 425 /* TooEarly */) shouldBe (true)
            AsyncOps.canRetry(status = 429 /* TooManyRequests */) shouldBe (true)

            // 5xx
            AsyncOps.canRetry(status = 500 /* InternalServerError */) shouldBe (true)
            AsyncOps.canRetry(status = 501 /* NotImplemented */) shouldBe (false)
            AsyncOps.canRetry(status = 502 /* BadGateway */) shouldBe (true)
            AsyncOps.canRetry(status = 503 /* ServiceUnavailable */) shouldBe (true)
            AsyncOps.canRetry(status = 504 /* GatewayTimeout */) shouldBe (true)
            AsyncOps.canRetry(status = 509 /* BandwidthLimitExceeded */) shouldBe (true)
            AsyncOps.canRetry(status = 598 /* NetworkReadTimeout */) shouldBe (true)
            AsyncOps.canRetry(status = 599 /* NetworkConnectTimeout */) shouldBe (true)
        }
    }
})
