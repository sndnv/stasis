package stasis.client_android.lib.security

import com.squareup.moshi.JsonClass
import org.jose4j.jwt.NumericDate

import org.jose4j.jwt.consumer.JwtConsumer
import org.jose4j.jwt.consumer.JwtConsumerBuilder
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Companion.map

@Suppress("ConstructorParameterNaming")
@JsonClass(generateAdapter = true)
data class AccessTokenResponse(
    val access_token: String,
    val refresh_token: String?,
    val expires_in: Long,
    val scope: String?
) {
    val claims: Try<Claims>
        get() = Try {
            extractor
                .processToClaims(access_token)
                .flattenClaims()
                .map { (name, values) -> name to values.joinToString { it.toString() } }
                .toMap()
        }.map { Claims(it) }

    val hasNotExpired: Boolean
        get() = Try {
            extractor.processToClaims(access_token).expirationTime.isAfter(NumericDate.now())
        }.getOrElse { false }

    data class Claims(private val claims: Map<String, String>) {
        val subject: String? = claims["sub"]
    }

    companion object {
        private val extractor: JwtConsumer by lazy {
            JwtConsumerBuilder()
                .setSkipSignatureVerification()
                .setSkipDefaultAudienceValidation()
                .build()
        }
    }
}
