package stasis.test.specs.unit.shared.interop.core

import stasis.shared.api.Formats._
import stasis.shared.api.responses.DeletedPendingDestaging
import stasis.test.specs.unit.shared.interop.InteropSpec

class PendingDestagingInteropSpec extends InteropSpec {
  "PendingDestaging interop" should "decode and re-encode DeletedPendingDestaging" in {
    assert(
      domain = "core",
      resource = "DeletedPendingDestaging",
      matches = DeletedPendingDestaging(existing = true)
    )
  }
}
