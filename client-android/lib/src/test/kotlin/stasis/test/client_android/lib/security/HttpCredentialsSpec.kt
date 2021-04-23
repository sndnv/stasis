package stasis.test.client_android.lib.security

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okhttp3.Request
import stasis.client_android.lib.security.HttpCredentials
import stasis.client_android.lib.security.HttpCredentials.Companion.withCredentials

class HttpCredentialsSpec : WordSpec({
    "HttpCredentials" should {
        "support extending/updating HTTP requests" {
            Request.Builder()
                .url("http://localhost")
                .build()
                .header(HttpCredentials.AuthorizationHeader) shouldBe (null)

            Request.Builder()
                .url("http://localhost")
                .withCredentials(HttpCredentials.None)
                .build()
                .header(HttpCredentials.AuthorizationHeader) shouldBe (null)

            Request.Builder()
                .url("http://localhost")
                .withCredentials(HttpCredentials.BasicHttpCredentials(username = "test-user", "test-password"))
                .build()
                .header(HttpCredentials.AuthorizationHeader) shouldBe ("Basic dGVzdC11c2VyOnRlc3QtcGFzc3dvcmQ=")

            Request.Builder()
                .url("http://localhost")
                .withCredentials(HttpCredentials.OAuth2BearerToken(token = "test-token"))
                .build()
                .header(HttpCredentials.AuthorizationHeader) shouldBe ("Bearer test-token")
        }
    }
})
