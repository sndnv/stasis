package stasis.client_android.lib.api.clients

import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString
import stasis.client_android.lib.api.clients.internal.ClientExtensions
import stasis.client_android.lib.model.server.devices.DeviceBootstrapParameters
import stasis.client_android.lib.security.HttpCredentials
import stasis.client_android.lib.security.HttpCredentials.Companion.withCredentials
import stasis.client_android.lib.utils.AsyncOps.async

class DefaultServerBootstrapEndpointClient(
    serverBootstrapUrl: String,
) : ServerBootstrapEndpointClient, ClientExtensions() {
    override val server: String = serverBootstrapUrl.trimEnd { it == '/' }

    override val credentials: suspend () -> HttpCredentials = { HttpCredentials.None }

    override suspend fun execute(bootstrapCode: String): DeviceBootstrapParameters {
        val request = Request.Builder()
            .url("$server/devices/execute")
            .put(ByteString.EMPTY.toRequestBody())
            .withCredentials(HttpCredentials.OAuth2BearerToken(token = bootstrapCode))
            .build()

        return client.newCall(request).async().toRequiredModel()
    }
}
