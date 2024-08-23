package stasis.client_android.lib.api.clients

import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString
import stasis.client_android.lib.api.clients.exceptions.EndpointFailure
import stasis.client_android.lib.api.clients.exceptions.InvalidBootstrapCodeFailure
import stasis.client_android.lib.api.clients.internal.ClientExtensions
import stasis.client_android.lib.model.server.devices.DeviceBootstrapParameters
import stasis.client_android.lib.security.HttpCredentials
import stasis.client_android.lib.security.HttpCredentials.Companion.withCredentials
import stasis.client_android.lib.utils.AsyncOps
import stasis.client_android.lib.utils.AsyncOps.asyncRetryWith
import stasis.client_android.lib.utils.NonFatal.nonFatal
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.lib.utils.Try.Success

class DefaultServerBootstrapEndpointClient(
    serverBootstrapUrl: String,
) : ServerBootstrapEndpointClient, ClientExtensions() {
    override val server: String = serverBootstrapUrl.trimEnd { it == '/' }

    override val credentials: suspend () -> HttpCredentials = { HttpCredentials.None }

    override val retryConfig: AsyncOps.RetryConfig = AsyncOps.RetryConfig.Default

    override suspend fun execute(bootstrapCode: String): Try<DeviceBootstrapParameters> {
        val request = Request.Builder()
            .url("$server/v1/devices/execute")
            .put(ByteString.EMPTY.toRequestBody())
            .withCredentials(HttpCredentials.OAuth2BearerToken(token = bootstrapCode))
            .build()

        return try {
            val response = client.newCall(request).asyncRetryWith(retryConfig)
            when {
                response.isSuccessful -> Success(response.toRequiredModel())
                response.code == StatusUnauthorized -> Failure(InvalidBootstrapCodeFailure())
                else -> Failure(
                    EndpointFailure(
                        "Server [$server] responded with [${response.code} - ${response.message}]"
                    )
                )
            }
        } catch (e: Throwable) {
            Failure(e.nonFatal())
        }
    }

    companion object {
        const val StatusUnauthorized: Int = 401
    }
}
