package stasis.client_android.lib.discovery.http

import stasis.client_android.lib.api.clients.internal.ClientExtensions
import stasis.client_android.lib.discovery.ServiceDiscoveryClient
import stasis.client_android.lib.discovery.ServiceDiscoveryResult
import stasis.client_android.lib.security.HttpCredentials
import stasis.client_android.lib.utils.AsyncOps
import stasis.client_android.lib.utils.Try

class HttpServiceDiscoveryClient(
    apiUrl: String,
    override val credentials: suspend () -> HttpCredentials,
    override val attributes: ServiceDiscoveryClient.Attributes
) : ServiceDiscoveryClient, ClientExtensions() {
    override val retryConfig: AsyncOps.RetryConfig = AsyncOps.RetryConfig.Default

    private val server: String = apiUrl.trimEnd { it == '/' }

    override suspend fun latest(isInitialRequest: Boolean): Try<ServiceDiscoveryResult> =
        jsonRequest<ServiceDiscoveryResult> { builder ->
            builder
                .url("$server/v1/discovery/provide")
                .post(attributes.asServiceDiscoveryRequest(isInitialRequest).toBody())
        }
}
