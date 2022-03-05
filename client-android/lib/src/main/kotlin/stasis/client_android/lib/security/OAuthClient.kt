package stasis.client_android.lib.security

import stasis.client_android.lib.utils.Try

interface OAuthClient {
    suspend fun token(scope: String?, parameters: GrantParameters): Try<AccessTokenResponse>

    object GrantType {
        const val ClientCredentials: String = "client_credentials"
        const val ResourceOwnerPasswordCredentials: String = "password"
        const val RefreshToken: String = "refresh_token"
    }

    sealed class GrantParameters {
        object ClientCredentials : GrantParameters()
        data class ResourceOwnerPasswordCredentials(val username: String, val password: String) : GrantParameters()
        data class RefreshToken(val refreshToken: String) : GrantParameters()
    }
}
