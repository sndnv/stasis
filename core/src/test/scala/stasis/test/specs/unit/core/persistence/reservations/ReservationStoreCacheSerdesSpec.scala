package stasis.test.specs.unit.core.persistence.reservations

import stasis.core.packaging.Crate
import stasis.core.persistence.CrateStorageReservation
import stasis.core.persistence.reservations.ReservationStoreCacheSerdes
import stasis.core.routing.Node
import stasis.test.specs.unit.UnitSpec

class ReservationStoreCacheSerdesSpec extends UnitSpec {
  "ReservationStoreCacheSerdes" should "serialize and deserialize keys" in {
    val key = (Crate.generateId(), Node.generateId())

    val serialized = ReservationStoreCacheSerdes.serializeKey(key)
    val deserialized = ReservationStoreCacheSerdes.deserializeKey(serialized)

    deserialized should be(key)
  }

  they should "fail deserializing if an invalid key is provided" in {
    an[IllegalArgumentException] should be thrownBy ReservationStoreCacheSerdes.deserializeKey("invalid-key")
  }

  they should "serialize and deserialize values" in {
    val reservation = CrateStorageReservation.generateId()

    val serialized = ReservationStoreCacheSerdes.serializeValue(reservation)
    val deserialized = ReservationStoreCacheSerdes.deserializeValue(serialized)

    deserialized should be(reservation)
  }
}
