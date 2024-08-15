package stasis.client_android.api.clients

import org.jose4j.jwk.RsaJwkGenerator
import org.jose4j.jws.AlgorithmIdentifiers
import org.jose4j.jws.JsonWebSignature
import org.jose4j.jwt.JwtClaims
import stasis.client_android.lib.security.AccessTokenResponse
import stasis.client_android.lib.security.OAuthClient
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Success

class MockOAuthClient : OAuthClient {

    override suspend fun token(scope: String?, parameters: OAuthClient.GrantParameters): Try<AccessTokenResponse> =
        Success(
            AccessTokenResponse(
                access_token = createJwt(),
                refresh_token = null,
                expires_in = 3600,
                scope = scope
            )
        )

    private val rsaKey = RsaJwkGenerator.generateJwk(2048).apply {
        keyId = "test-key"
    }

    private fun createJwt(): String {
        val claims = JwtClaims().apply {
            subject = "test-subject"
            setClaim("a", "b")
            setExpirationTimeMinutesInTheFuture(60.0f)
        }

        val jws = JsonWebSignature().apply {
            payload = claims.toJson()
            key = rsaKey.privateKey
            keyIdHeaderValue = rsaKey.keyId
            algorithmHeaderValue = AlgorithmIdentifiers.RSA_USING_SHA256
        }

        return jws.compactSerialization
    }
}