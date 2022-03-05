package stasis.client_android.lib.api.clients

import stasis.client_android.lib.model.server.devices.DeviceBootstrapParameters
import stasis.client_android.lib.utils.Try

interface ServerBootstrapEndpointClient {
    val server: String

    suspend fun execute(bootstrapCode: String): Try<DeviceBootstrapParameters>

    interface Factory {
        fun create(server: String): ServerBootstrapEndpointClient
    }
}
