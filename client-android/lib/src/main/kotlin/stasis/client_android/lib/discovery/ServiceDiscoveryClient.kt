package stasis.client_android.lib.discovery

import stasis.client_android.lib.utils.Try

interface ServiceDiscoveryClient : ServiceApiClient {
    val attributes: Attributes

    suspend fun latest(isInitialRequest: Boolean): Try<ServiceDiscoveryResult>

    interface Attributes {
        fun asServiceDiscoveryRequest(isInitialRequest: Boolean): ServiceDiscoveryRequest
    }
}
