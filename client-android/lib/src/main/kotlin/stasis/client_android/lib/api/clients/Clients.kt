package stasis.client_android.lib.api.clients

import stasis.client_android.lib.discovery.exceptions.DiscoveryFailure
import stasis.client_android.lib.discovery.providers.client.ServiceDiscoveryProvider
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Failure
import java.util.concurrent.atomic.AtomicReference

sealed interface Clients {
    val api: ServerApiEndpointClient
    val core: ServerCoreEndpointClient

    fun withDiscovery(discovery: Try<ServiceDiscoveryProvider>)

    data class Static(
        override val api: ServerApiEndpointClient,
        override val core: ServerCoreEndpointClient
    ) : Clients {
        override fun withDiscovery(discovery: Try<ServiceDiscoveryProvider>) = Unit // do nothing
    }

    class Discovered : Clients {
        private val discoveryRef: AtomicReference<Try<ServiceDiscoveryProvider>> =
            AtomicReference(Failure(DiscoveryFailure("No discovery provider found")))

        override val api: ServerApiEndpointClient
            get() = discoveryRef.get().get().latest(ServerApiEndpointClient::class.java)

        override val core: ServerCoreEndpointClient
            get() = discoveryRef.get().get().latest(ServerCoreEndpointClient::class.java)

        override fun withDiscovery(discovery: Try<ServiceDiscoveryProvider>) =
            discoveryRef.set(discovery)
    }

    companion object {
        operator fun invoke(api: ServerApiEndpointClient, core: ServerCoreEndpointClient): Clients =
            Static(api = api, core = core)

        fun discovered(): Clients =
            Discovered()
    }
}
