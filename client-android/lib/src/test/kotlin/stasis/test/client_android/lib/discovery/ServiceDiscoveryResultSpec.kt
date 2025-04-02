package stasis.test.client_android.lib.discovery

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.discovery.ServiceApiEndpoint
import stasis.client_android.lib.discovery.ServiceDiscoveryResult
import stasis.client_android.lib.model.core.networking.EndpointAddress

class ServiceDiscoveryResultSpec : WordSpec({
    "A ServiceDiscoveryResult" should {
        "support rendering as a string" {
            ServiceDiscoveryResult.KeepExisting.asString shouldBe ("result=keep-existing")

            ServiceDiscoveryResult.SwitchTo(
                endpoints = ServiceDiscoveryResult.Endpoints(
                    api = ServiceApiEndpoint.Api(uri = "test-api"),
                    core = ServiceApiEndpoint.Core(address = EndpointAddress.HttpEndpointAddress(uri = "test-core")),
                    discovery = ServiceApiEndpoint.Discovery(uri = "test-discovery")
                ),
                recreateExisting = false
            ).asString shouldBe (
                    "result=switch-to,endpoints=api__test-api;" +
                            "core_http__test-core;" +
                            "discovery__test-discovery,recreate-existing=false"
                    )
        }
    }

    "ServiceDiscoveryResult Endpoints" should {
        "support rendering as a string" {
            val endpoints = ServiceDiscoveryResult.Endpoints(
                api = ServiceApiEndpoint.Api(uri = "test-api"),
                core = ServiceApiEndpoint.Core(address = EndpointAddress.HttpEndpointAddress(uri = "test-core")),
                discovery = ServiceApiEndpoint.Discovery(uri = "test-discovery")
            )

            endpoints.asString shouldBe ("api__test-api;core_http__test-core;discovery__test-discovery")
        }
    }
})
