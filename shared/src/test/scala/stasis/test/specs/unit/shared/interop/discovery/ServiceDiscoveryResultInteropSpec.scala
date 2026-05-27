package stasis.test.specs.unit.shared.interop.discovery

import stasis.core.api.Formats._
import stasis.core.discovery.ServiceApiEndpoint
import stasis.core.discovery.ServiceDiscoveryResult
import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.http.HttpEndpointAddress
import stasis.test.specs.unit.shared.interop.InteropSpec

class ServiceDiscoveryResultInteropSpec extends InteropSpec {
  "ServiceDiscoveryResult interop" should "decode and re-encode KeepExisting" in {
    assert[ServiceDiscoveryResult](
      domain = "discovery",
      resource = "ServiceDiscoveryResult.keep-existing",
      matches = ServiceDiscoveryResult.KeepExisting
    )
  }

  it should "decode and re-encode SwitchTo with an HTTP core endpoint" in {
    assert[ServiceDiscoveryResult](
      domain = "discovery",
      resource = "ServiceDiscoveryResult.switch-to-http",
      matches = ServiceDiscoveryResult.SwitchTo(
        endpoints = ServiceDiscoveryResult.Endpoints(
          api = ServiceApiEndpoint.Api(uri = "https://api.example.test"),
          core = ServiceApiEndpoint.Core(
            address = HttpEndpointAddress("https://core.example.test")
          ),
          discovery = ServiceApiEndpoint.Discovery(uri = "https://discovery.example.test")
        ),
        recreateExisting = false
      )
    )
  }

  it should "decode and re-encode SwitchTo with a gRPC core endpoint" in {
    assert[ServiceDiscoveryResult](
      domain = "discovery",
      resource = "ServiceDiscoveryResult.switch-to-grpc",
      matches = ServiceDiscoveryResult.SwitchTo(
        endpoints = ServiceDiscoveryResult.Endpoints(
          api = ServiceApiEndpoint.Api(uri = "https://api.example.test"),
          core = ServiceApiEndpoint.Core(
            address = GrpcEndpointAddress(host = "core.example.test", port = 9999, tlsEnabled = true)
          ),
          discovery = ServiceApiEndpoint.Discovery(uri = "https://discovery.example.test")
        ),
        recreateExisting = true
      )
    )
  }
}
