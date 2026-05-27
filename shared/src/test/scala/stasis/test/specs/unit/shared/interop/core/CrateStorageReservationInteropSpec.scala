package stasis.test.specs.unit.shared.interop.core

import stasis.shared.api.Formats._
import stasis.shared.api.responses.DeletedReservation
import stasis.test.specs.unit.shared.interop.InteropSpec

class CrateStorageReservationInteropSpec extends InteropSpec {
  "CrateStorageReservation interop" should "decode and re-encode DeletedReservation" in {
    assert(
      domain = "core",
      resource = "DeletedReservation",
      matches = DeletedReservation(existing = true)
    )
  }
}
