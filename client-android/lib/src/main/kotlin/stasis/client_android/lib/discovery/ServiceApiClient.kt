package stasis.client_android.lib.discovery

interface ServiceApiClient {
    interface Factory {
        fun create(endpoint: ServiceApiEndpoint.Api, coreClient: ServiceApiClient): ServiceApiClient
        fun create(endpoint: ServiceApiEndpoint.Core): ServiceApiClient
        fun create(endpoint: ServiceApiEndpoint.Discovery): ServiceApiClient
    }
}
