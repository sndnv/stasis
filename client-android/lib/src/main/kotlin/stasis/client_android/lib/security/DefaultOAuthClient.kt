package stasis.client_android.lib.security

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import stasis.client_android.lib.api.clients.exceptions.AccessDeniedFailure
import stasis.client_android.lib.api.clients.exceptions.EndpointFailure
import stasis.client_android.lib.api.clients.internal.ClientExtensions
import stasis.client_android.lib.security.HttpCredentials.Companion.withCredentials
import stasis.client_android.lib.utils.AsyncOps.async
import stasis.client_android.lib.utils.NonFatal.nonFatal
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Companion.flatMap
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.lib.utils.Try.Success

class DefaultOAuthClient(
    private val tokenEndpoint: String,
    client: String,
    clientSecret: String
) : OAuthClient {
    private val credentials =
        HttpCredentials.BasicHttpCredentials(username = client, password = clientSecret)

    private val http: OkHttpClient = OkHttpClient()

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    override suspend fun token(
        scope: String?,
        parameters: OAuthClient.GrantParameters
    ): Try<AccessTokenResponse> {
        val scopeParams = scope?.let { mapOf("scope" to it) } ?: emptyMap()

        val grantParams = when (parameters) {
            is OAuthClient.GrantParameters.ClientCredentials -> mapOf(
                "grant_type" to OAuthClient.GrantType.ClientCredentials
            )
            is OAuthClient.GrantParameters.ResourceOwnerPasswordCredentials -> mapOf(
                "grant_type" to OAuthClient.GrantType.ResourceOwnerPasswordCredentials,
                "username" to parameters.username,
                "password" to parameters.password
            )
            is OAuthClient.GrantParameters.RefreshToken -> mapOf(
                "grant_type" to OAuthClient.GrantType.RefreshToken,
                "refresh_token" to parameters.refreshToken
            )
        }

        return try {
            val body = (scopeParams + grantParams).toList()
                .fold(FormBody.Builder()) { builder, (paramName, paramValue) ->
                    builder.add(name = paramName, value = paramValue)
                }.build()

            val response = jsonRequest<AccessTokenResponse> { builder ->
                builder
                    .url(tokenEndpoint)
                    .post(body = body)
            }

            response
        } catch (e: Throwable) {
            Failure(e.nonFatal())
        }
    }


    private suspend inline fun request(block: (Request.Builder) -> Request.Builder): Response {
        val request = block(Request.Builder().withCredentials(credentials)).build()
        return http.newCall(request).async()
    }

    private fun Response.successful(): Try<Response> = when {
        this.isSuccessful -> Success(this)
        this.code == ClientExtensions.StatusUnauthorized -> Failure(AccessDeniedFailure())
        else -> Failure(EndpointFailure("Server responded with [${this.code} - ${this.message}]"))
    }

    private suspend inline fun <reified T> jsonRequest(block: (Request.Builder) -> Request.Builder): Try<T> =
        request(block).successful().flatMap { response ->
            val result = withContext(Dispatchers.IO) {
                response.body?.use {
                    moshi.adapter(T::class.java).fromJson(it.source())
                }
            }

            when (result) {
                null -> Failure(EndpointFailure("Expected response data but none was found"))
                else -> Success(result)
            }
        }


    companion object {
        const val StatusUnauthorized: Int = 401
    }
}
