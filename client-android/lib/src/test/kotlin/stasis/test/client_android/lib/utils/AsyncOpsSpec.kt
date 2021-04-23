package stasis.test.client_android.lib.utils

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import stasis.client_android.lib.utils.AsyncOps.async

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
    }
})
