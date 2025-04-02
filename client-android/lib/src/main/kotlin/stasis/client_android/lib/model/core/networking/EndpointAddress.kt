package stasis.client_android.lib.model.core.networking

sealed class EndpointAddress {
    data class HttpEndpointAddress(
        val uri: String
    ) : EndpointAddress()

    data class GrpcEndpointAddress(
        val host: String,
        val port: Int,
        val tlsEnabled: Boolean
    ) : EndpointAddress()
}
