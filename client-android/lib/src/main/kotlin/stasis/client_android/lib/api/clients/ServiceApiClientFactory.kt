package stasis.client_android.lib.api.clients

import stasis.client_android.lib.discovery.ServiceApiClient
import stasis.client_android.lib.discovery.ServiceApiEndpoint
import stasis.client_android.lib.discovery.ServiceDiscoveryClient
import stasis.client_android.lib.model.core.networking.EndpointAddress

class ServiceApiClientFactory(
    private val createServerCoreEndpointClient: (String) -> ServerCoreEndpointClient,
    private val createServerApiEndpointClient: (String, ServerCoreEndpointClient) -> ServerApiEndpointClient,
    private val createServiceDiscoveryClient: (String) -> ServiceDiscoveryClient
) : ServiceApiClient.Factory {
    override fun create(endpoint: ServiceApiEndpoint.Api, coreClient: ServiceApiClient): ServiceApiClient =
        createServerApiEndpointClient(
            endpoint.uri,
            when (coreClient) {
                is ServerCoreEndpointClient -> coreClient
                else -> throw IllegalArgumentException(
                    "Cannot create API endpoint client with core client of type [${coreClient.javaClass.simpleName}]"
                )
            }
        )

    override fun create(endpoint: ServiceApiEndpoint.Core): ServiceApiClient =
        when (val address = endpoint.address) {
            is EndpointAddress.HttpEndpointAddress -> createServerCoreEndpointClient(address.uri)
            else -> throw IllegalArgumentException(
                "Cannot create core endpoint client for address of type [${address.javaClass.simpleName}]"
            )
        }

    override fun create(endpoint: ServiceApiEndpoint.Discovery): ServiceApiClient =
        createServiceDiscoveryClient(endpoint.uri)
}
