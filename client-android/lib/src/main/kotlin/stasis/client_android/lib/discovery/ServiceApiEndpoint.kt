package stasis.client_android.lib.discovery

import stasis.client_android.lib.model.core.networking.EndpointAddress

sealed class ServiceApiEndpoint {
    abstract val id: String

    data class Api(val uri: String) : ServiceApiEndpoint() {
        override val id: String by lazy { "api__$uri" }
    }

    data class Core(val address: EndpointAddress) : ServiceApiEndpoint() {
        override val id: String by lazy {
            when (address) {
                is EndpointAddress.HttpEndpointAddress -> "core_http__${address.uri}"
                is EndpointAddress.GrpcEndpointAddress -> "core_grpc__${address.host}:${address.port}"
            }
        }

    }

    data class Discovery(val uri: String) : ServiceApiEndpoint() {
        override val id: String by lazy { "discovery__$uri" }
    }
}
