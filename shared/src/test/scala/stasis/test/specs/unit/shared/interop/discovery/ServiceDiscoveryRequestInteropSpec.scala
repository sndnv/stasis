package stasis.test.specs.unit.shared.interop.discovery

import stasis.core.api.Formats._
import stasis.core.discovery.ServiceDiscoveryRequest
import stasis.test.specs.unit.shared.interop.InteropSpec

class ServiceDiscoveryRequestInteropSpec extends InteropSpec {
  "ServiceDiscoveryRequest interop" should "decode and re-encode a request" in {
    assert(
      domain = "discovery",
      resource = "ServiceDiscoveryRequest",
      matches = ServiceDiscoveryRequest(
        isInitialRequest = false,
        attributes = Map(
          "device" -> "1a68637d-e3cd-47d0-b683-ddf99b064056",
          "node" -> "2df1bd32-0dfe-4a6e-9981-589c7180206b",
          "user" -> "cef57a23-22c1-4b87-82e4-56ee622ead27"
        )
      )
    )
  }
}
