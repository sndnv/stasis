package stasis.test.specs.unit.core.discovery

import stasis.core.discovery.ServiceDiscoveryRequest
import io.github.sndnv.layers.testing.UnitSpec

class ServiceDiscoveryRequestSpec extends UnitSpec {
  "A ServiceDiscoveryRequest" should "support converting its attributes to a request ID" in {
    val request = ServiceDiscoveryRequest(
      isInitialRequest = false,
      attributes = Map(
        "b" -> "42",
        "c" -> "false",
        "a" -> "test-string"
      )
    )

    request.id should be("a=test-string::b=42::c=false")
  }
}
