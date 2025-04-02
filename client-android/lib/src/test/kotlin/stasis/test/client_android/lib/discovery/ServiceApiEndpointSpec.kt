package stasis.test.client_android.lib.discovery

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.discovery.ServiceApiEndpoint
import stasis.client_android.lib.model.core.networking.EndpointAddress

class ServiceApiEndpointSpec : WordSpec({
    "A ServiceApiEndpoint" should {
        "support providing an ID" {
            ServiceApiEndpoint.Api(uri = "test-uri").id shouldBe ("api__test-uri")

            ServiceApiEndpoint.Core(
                address = EndpointAddress.HttpEndpointAddress(uri = "test-uri")
            ).id shouldBe ("core_http__test-uri")

            ServiceApiEndpoint.Core(
                address = EndpointAddress.GrpcEndpointAddress(host = "test-host", port = 1234, tlsEnabled = false)
            ).id shouldBe ("core_grpc__test-host:1234")
        }
    }
})
