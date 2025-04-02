package stasis.test.client_android.lib.mocks

import stasis.client_android.lib.discovery.ServiceDiscoveryClient
import stasis.client_android.lib.discovery.ServiceDiscoveryRequest
import stasis.client_android.lib.discovery.ServiceDiscoveryResult
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Success

class MockServiceDiscoveryClient(
    private val initialDiscoveryResult: ServiceDiscoveryResult,
    private val nextDiscoveryResult: ServiceDiscoveryResult
) : ServiceDiscoveryClient {
    override val attributes: ServiceDiscoveryClient.Attributes = TestAttributes(a = "b")

    override suspend fun latest(isInitialRequest: Boolean): Try<ServiceDiscoveryResult> =
        Success(
            value = if (isInitialRequest) initialDiscoveryResult
            else nextDiscoveryResult
        )

    data class TestAttributes(val a: String) : ServiceDiscoveryClient.Attributes {
        override fun asServiceDiscoveryRequest(isInitialRequest: Boolean): ServiceDiscoveryRequest =
            ServiceDiscoveryRequest(isInitialRequest = isInitialRequest, attributes = mapOf("a" to a))
    }

    companion object {
        operator fun invoke(): MockServiceDiscoveryClient =
            MockServiceDiscoveryClient(
                initialDiscoveryResult = ServiceDiscoveryResult.KeepExisting,
                nextDiscoveryResult = ServiceDiscoveryResult.KeepExisting
            )
    }
}
