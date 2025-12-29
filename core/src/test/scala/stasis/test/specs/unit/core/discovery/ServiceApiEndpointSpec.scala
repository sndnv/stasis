package stasis.test.specs.unit.core.discovery

import stasis.core.discovery.ServiceApiEndpoint
import stasis.core.networking.EndpointAddress
import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.http.HttpEndpointAddress
import io.github.sndnv.layers.testing.UnitSpec

class ServiceApiEndpointSpec extends UnitSpec {
  "A ServiceApiEndpoint" should "support providing an ID" in {
    ServiceApiEndpoint.Api(uri = "test-uri").id should be("api__test-uri")

    ServiceApiEndpoint
      .Core(
        address = HttpEndpointAddress(uri = "test-uri")
      )
      .id should be("core_http__test-uri")

    ServiceApiEndpoint
      .Core(
        address = GrpcEndpointAddress(host = "test-host", port = 1234, tlsEnabled = false)
      )
      .id should be("core_grpc__test-host:1234")

    an[IllegalArgumentException] should be thrownBy ServiceApiEndpoint.Core(address = new EndpointAddress {}).id

    ServiceApiEndpoint.Discovery(uri = "test-uri").id should be("discovery__test-uri")
  }
}
