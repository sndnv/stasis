package stasis.test.specs.unit.core.discovery

import stasis.core.discovery.ServiceApiEndpoint
import stasis.core.discovery.ServiceDiscoveryResult
import stasis.core.networking.http.HttpEndpointAddress
import stasis.layers.UnitSpec

class ServiceDiscoveryResultSpec extends UnitSpec {
  "A ServiceDiscoveryResult" should "support rendering as a string" in {
    ServiceDiscoveryResult.KeepExisting.asString should be("result=keep-existing")

    ServiceDiscoveryResult
      .SwitchTo(
        endpoints = ServiceDiscoveryResult.Endpoints(
          api = ServiceApiEndpoint.Api(uri = "test-api"),
          core = ServiceApiEndpoint.Core(address = HttpEndpointAddress(uri = "test-core")),
          discovery = ServiceApiEndpoint.Discovery(uri = "test-discovery")
        ),
        recreateExisting = false
      )
      .asString should be(
      "result=switch-to,endpoints=api__test-api;core_http__test-core;discovery__test-discovery,recreate-existing=false"
    )
  }

  "ServiceDiscoveryResult Endpoints" should "support rendering as a string" in {
    val endpoints = ServiceDiscoveryResult.Endpoints(
      api = ServiceApiEndpoint.Api(uri = "test-api"),
      core = ServiceApiEndpoint.Core(address = HttpEndpointAddress(uri = "test-core")),
      discovery = ServiceApiEndpoint.Discovery(uri = "test-discovery")
    )

    endpoints.asString should be("api__test-api;core_http__test-core;discovery__test-discovery")
  }
}
