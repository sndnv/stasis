package stasis.client_android.lib.discovery

sealed class ServiceDiscoveryResult {
    abstract val asString: String

    data object KeepExisting : ServiceDiscoveryResult() {
        override val asString: String = "result=keep-existing"
    }

    data class SwitchTo(
        val endpoints: Endpoints,
        val recreateExisting: Boolean
    ) : ServiceDiscoveryResult() {
        override val asString: String by lazy {
            "result=switch-to,endpoints=${endpoints.asString},recreate-existing=$recreateExisting"
        }
    }

    data class Endpoints(
        val api: ServiceApiEndpoint.Api,
        val core: ServiceApiEndpoint.Core,
        val discovery: ServiceApiEndpoint.Discovery
    ) {
        val asString: String by lazy {
            listOf(api.id, core.id, discovery.id).joinToString(separator = ";")
        }
    }
}
