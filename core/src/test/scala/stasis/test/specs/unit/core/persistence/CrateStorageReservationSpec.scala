package stasis.test.specs.unit.core.persistence

import java.util.UUID

import stasis.core.persistence.CrateStorageReservation
import stasis.test.specs.unit.UnitSpec

class CrateStorageReservationSpec extends UnitSpec {
  "A CrateStorageReservation" should "be representable as string" in {
    val id = "2691a0b1-38e7-46d6-9d14-cc265a818c74"
    val crate = "303da836-3abe-4376-bf9f-45c9acb643d7"
    val origin = "f266df3b-5c5d-46eb-8ad9-525da813af4f"
    val target = "9d567d2d-8055-486f-8c19-0cd4e0861ab5"

    val request = CrateStorageReservation(
      id = UUID.fromString(id),
      crate = UUID.fromString(crate),
      size = 42,
      copies = 3,
      origin = UUID.fromString(origin),
      target = UUID.fromString(target)
    )

    request.toString should be(
      s"CrateStorageReservation(id=$id,crate=$crate,size=42,copies=3,origin=$origin,target=$target)"
    )
  }
}
