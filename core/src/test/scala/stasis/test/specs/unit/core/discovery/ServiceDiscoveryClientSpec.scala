package stasis.test.specs.unit.core.discovery

import stasis.core.discovery.ServiceDiscoveryClient
import stasis.core.discovery.ServiceDiscoveryRequest
import stasis.layers.UnitSpec

class ServiceDiscoveryClientSpec extends UnitSpec {
  import ServiceDiscoveryClientSpec.TestAttributes

  "ServiceDiscoveryClient Attributes" should "support conversion to a discovery requesst" in {
    val expected = ServiceDiscoveryRequest(
      isInitialRequest = false,
      attributes = Map(
        "a" -> "test-string",
        "b" -> "42",
        "c" -> "false"
      )
    )

    val actual = TestAttributes(
      a = "test-string",
      b = 42,
      c = false
    ).asServiceDiscoveryRequest(isInitialRequest = false)

    actual should be(expected)
  }
}

object ServiceDiscoveryClientSpec {
  final case class TestAttributes(a: String, b: Int, c: Boolean) extends ServiceDiscoveryClient.Attributes
}
