package stasis.test.client_android.lib.security

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.jose4j.jwk.RsaJwkGenerator
import org.jose4j.jws.AlgorithmIdentifiers
import org.jose4j.jws.JsonWebSignature
import org.jose4j.jwt.JwtClaims
import org.jose4j.jwt.consumer.InvalidJwtException
import stasis.client_android.lib.security.AccessTokenResponse
import stasis.client_android.lib.utils.Try.Success

class AccessTokenResponseSpec : WordSpec({
    "An AccessTokenResponse" should {

        val rsaKey = RsaJwkGenerator.generateJwk(2048).apply {
            keyId = "test-key"
        }

        fun createJwt(expiresIn: Float? = null): String {
            val claims = JwtClaims().apply {
                subject = "test-subject"
                setClaim("a", "b")
                expiresIn?.let { setExpirationTimeMinutesInTheFuture(it) }
            }

            val jws = JsonWebSignature().apply {
                payload = claims.toJson()
                key = rsaKey.privateKey
                keyIdHeaderValue = rsaKey.keyId
                algorithmHeaderValue = AlgorithmIdentifiers.RSA_USING_SHA256
            }

            return jws.compactSerialization
        }

        "support providing JWT claims" {
            val validResponse = AccessTokenResponse(
                access_token = createJwt(),
                refresh_token = null,
                expires_in = 1,
                scope = null
            )

            val invalidResponse = AccessTokenResponse(
                access_token = "test-token",
                refresh_token = null,
                expires_in = 1,
                scope = null
            )

            validResponse.claims shouldBe (Success(AccessTokenResponse.Claims(mapOf("sub" to "test-subject", "a" to "b"))))

            val e = shouldThrow<InvalidJwtException> { invalidResponse.claims.get() }
            e.message shouldStartWith ("JWT processing failed")
        }

        "support checking if a token has expired" {
            val validResponse = AccessTokenResponse(
                access_token = createJwt(expiresIn = 1.0f),
                refresh_token = null,
                expires_in = 1,
                scope = null
            )

            val expiredResponse = AccessTokenResponse(
                access_token = createJwt(expiresIn = -1.0f),
                refresh_token = null,
                expires_in = 1,
                scope = null
            )

            validResponse.hasNotExpired shouldBe (true)
            expiredResponse.hasNotExpired shouldBe (false)
        }

        "support providing token subject from claims" {
            val validClaims = AccessTokenResponse.Claims(mapOf("sub" to "test-subject", "a" to "b"))
            val invalidClaims = AccessTokenResponse.Claims(emptyMap())

            validClaims.subject shouldBe ("test-subject")
            invalidClaims.subject shouldBe (null)
        }
    }
})
