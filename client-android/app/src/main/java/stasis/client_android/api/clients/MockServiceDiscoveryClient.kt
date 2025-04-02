package stasis.client_android.api.clients

import stasis.client_android.lib.discovery.ServiceDiscoveryClient
import stasis.client_android.lib.discovery.ServiceDiscoveryRequest
import stasis.client_android.lib.discovery.ServiceDiscoveryResult
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Success

class MockServiceDiscoveryClient : ServiceDiscoveryClient {
    override val attributes: ServiceDiscoveryClient.Attributes =
        object : ServiceDiscoveryClient.Attributes {
            override fun asServiceDiscoveryRequest(isInitialRequest: Boolean): ServiceDiscoveryRequest =
                ServiceDiscoveryRequest(
                    isInitialRequest = isInitialRequest,
                    attributes = emptyMap()
                )
        }

    override suspend fun latest(isInitialRequest: Boolean): Try<ServiceDiscoveryResult> =
        Success(ServiceDiscoveryResult.KeepExisting)
}
