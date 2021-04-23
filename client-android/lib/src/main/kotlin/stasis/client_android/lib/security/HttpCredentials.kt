package stasis.client_android.lib.security

import okhttp3.Credentials
import okhttp3.Request

sealed class HttpCredentials {
    object None : HttpCredentials()
    data class BasicHttpCredentials(val username: String, val password: String) : HttpCredentials()
    data class OAuth2BearerToken(val token: String) : HttpCredentials()

    companion object {
        const val AuthorizationHeader: String = "Authorization"

        fun Request.Builder.withCredentials(credentials: HttpCredentials): Request.Builder =
            when (credentials) {
                is None -> this
                is BasicHttpCredentials -> header(
                    AuthorizationHeader,
                    Credentials.basic(credentials.username, credentials.password)
                )
                is OAuth2BearerToken -> header(
                    AuthorizationHeader,
                    "Bearer ${credentials.token}"
                )
            }
    }
}
