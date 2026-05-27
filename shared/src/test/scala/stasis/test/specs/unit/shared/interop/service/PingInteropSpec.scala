package stasis.test.specs.unit.shared.interop.service

import java.util.UUID

import stasis.shared.api.Formats._
import stasis.shared.api.responses.Ping
import stasis.test.specs.unit.shared.interop.InteropSpec

class PingInteropSpec extends InteropSpec {
  "Ping interop" should "decode and re-encode a ping" in {
    assert(
      domain = "service",
      resource = "Ping",
      matches = Ping(
        id = UUID.fromString("a55bf2c4-c270-4d8b-806b-9f993e8d415a")
      )
    )
  }
}
