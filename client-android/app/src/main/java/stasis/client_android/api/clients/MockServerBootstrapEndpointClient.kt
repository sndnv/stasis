package stasis.client_android.api.clients

import stasis.client_android.lib.api.clients.ServerBootstrapEndpointClient
import stasis.client_android.lib.model.server.devices.DeviceBootstrapParameters
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Success

class MockServerBootstrapEndpointClient : ServerBootstrapEndpointClient {
    override val server: String = MockConfig.ServerApi

    override suspend fun execute(bootstrapCode: String): Try<DeviceBootstrapParameters> =
        Success(MockConfig.BootstrapParameters)
}