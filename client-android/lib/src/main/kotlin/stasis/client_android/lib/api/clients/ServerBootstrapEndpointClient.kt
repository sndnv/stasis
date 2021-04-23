package stasis.client_android.lib.api.clients

import stasis.client_android.lib.model.server.devices.DeviceBootstrapParameters

interface ServerBootstrapEndpointClient {
    val server: String

    suspend fun execute(bootstrapCode: String): DeviceBootstrapParameters
}
