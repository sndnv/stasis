package stasis.test.specs.unit.core.discovery.providers.server

import stasis.core.discovery.ServiceApiEndpoint
import stasis.core.discovery.ServiceDiscoveryRequest
import stasis.core.discovery.ServiceDiscoveryResult
import stasis.core.discovery.providers.server.ServiceDiscoveryProvider
import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.http.HttpEndpointAddress
import stasis.layers.UnitSpec

class ServiceDiscoveryProviderSpec extends UnitSpec {
  "A ServiceDiscoveryProvider" should "be created from config" in {
    val config = com.typesafe.config.ConfigFactory.load().getConfig("stasis.test.core.discovery.server")

    ServiceDiscoveryProvider(config = config.getConfig("provider-disabled")).getClass.getName should endWith(
      "ServiceDiscoveryProvider$Disabled"
    )

    ServiceDiscoveryProvider(config = config.getConfig("provider-static")).getClass.getName should endWith(
      "ServiceDiscoveryProvider$Static"
    )

    val e = intercept[IllegalArgumentException](ServiceDiscoveryProvider(config = config.getConfig("provider-invalid")))
    e.getMessage should be("Unexpected provider type specified: [other]")
  }

  it should "fail if a static provided is enabled but no config file is provided" in {
    val config = com.typesafe.config.ConfigFactory.load().getConfig("stasis.test.core.discovery.server")

    val e = intercept[IllegalArgumentException](ServiceDiscoveryProvider(config = config.getConfig("provider-static-no-config")))

    e.getMessage should include("Static discovery enabled but no config file was provided")
  }

  "A Disabled ServiceDiscoveryProvider" should "not respond with new endpoints" in {
    val provider = new ServiceDiscoveryProvider.Disabled()

    provider.provide(request = null).map { result =>
      result should be(ServiceDiscoveryResult.KeepExisting)
    }
  }

  "A Static ServiceDiscoveryProvider" should "respond with new or existing endpoints based on the client" in {
    val provider = new ServiceDiscoveryProvider.Static(endpoints = endpoints)

    val request = ServiceDiscoveryRequest(isInitialRequest = false, attributes = Map("client" -> "test-client"))

    val switchToResult = ServiceDiscoveryResult.SwitchTo(
      endpoints = ServiceDiscoveryResult.Endpoints(
        api = ServiceApiEndpoint.Api(uri = "http://localhost:10000"),
        core = ServiceApiEndpoint.Core(address = HttpEndpointAddress(uri = "http://localhost:10001")),
        discovery = ServiceApiEndpoint.Discovery(uri = "http://localhost:10002")
      ),
      recreateExisting = false
    )

    for {
      unknownClientResult <- provider.provide(request = request) // request is not set as initial but client is unknown
      knownClientResult <- provider.provide(request = request) // client is known
      knownClientButSetAsInitialResult <- provider.provide(
        request = request.copy(isInitialRequest = true) // client is known but request is marked as initial
      )
    } yield {
      unknownClientResult should be(switchToResult)
      knownClientResult should be(ServiceDiscoveryResult.KeepExisting)
      knownClientButSetAsInitialResult should be(switchToResult)
    }
  }

  "Static ServiceDiscoveryProvider Endpoints" should "support loading from config" in {
    val config = com.typesafe.config.ConfigFactory.load("discovery-static.conf").getConfig("endpoints")

    val actual = ServiceDiscoveryProvider.Static.Endpoints(config = config)

    actual should be(endpoints)
  }

  they should "fail if no API endpoints are provided" in {
    an[IllegalArgumentException] should be thrownBy endpoints.copy(api = Seq.empty)
  }

  they should "fail if no core endpoints are provided" in {
    an[IllegalArgumentException] should be thrownBy endpoints.copy(core = Seq.empty)
  }

  they should "fail if unexpected core endpoints are provided" in {
    val config = com.typesafe.config.ConfigFactory.load("discovery-static-invalid.conf").getConfig("endpoints")

    val e = intercept[IllegalArgumentException](ServiceDiscoveryProvider.Static.Endpoints(config = config))

    e.getMessage should be("Unexpected endpoint type specified: [other]")
  }

  they should "support selecting endpoints based on requests" in {
    endpoints.select(forRequestId = "client=test-client") should be(
      ServiceDiscoveryResult.Endpoints(
        api = ServiceApiEndpoint.Api(uri = "http://localhost:10000"),
        core = ServiceApiEndpoint.Core(address = HttpEndpointAddress(uri = "http://localhost:10001")),
        discovery = ServiceApiEndpoint.Discovery(uri = "http://localhost:10002")
      )
    )

    endpoints.select(forRequestId = "other-request-id") should be(
      ServiceDiscoveryResult.Endpoints(
        api = ServiceApiEndpoint.Api(uri = "http://localhost:20000"),
        core = ServiceApiEndpoint.Core(address = HttpEndpointAddress(uri = "http://localhost:20001")),
        discovery = ServiceApiEndpoint.Discovery(uri = "http://localhost:10002")
      )
    )
  }

  they should "support selecting API endpoints as fallback when no discovery endpoints are available" in {
    endpoints.copy(discovery = Seq.empty).select(forRequestId = "client=test-client") should be(
      ServiceDiscoveryResult.Endpoints(
        api = ServiceApiEndpoint.Api(uri = "http://localhost:10000"),
        core = ServiceApiEndpoint.Core(address = HttpEndpointAddress(uri = "http://localhost:10001")),
        discovery = ServiceApiEndpoint.Discovery(uri = "http://localhost:10000")
      )
    )
  }

  private val endpoints = ServiceDiscoveryProvider.Static.Endpoints(
    api = Seq(
      ServiceApiEndpoint.Api(uri = "http://localhost:10000"),
      ServiceApiEndpoint.Api(uri = "http://localhost:20000"),
      ServiceApiEndpoint.Api(uri = "http://localhost:30000")
    ),
    core = Seq(
      ServiceApiEndpoint.Core(address = HttpEndpointAddress(uri = "http://localhost:10001")),
      ServiceApiEndpoint.Core(address = HttpEndpointAddress(uri = "http://localhost:20001")),
      ServiceApiEndpoint.Core(address = GrpcEndpointAddress(host = "localhost", port = 30001, tlsEnabled = false))
    ),
    discovery = Seq(
      ServiceApiEndpoint.Discovery(uri = "http://localhost:10002"),
      ServiceApiEndpoint.Discovery(uri = "http://localhost:20002")
    )
  )
}
