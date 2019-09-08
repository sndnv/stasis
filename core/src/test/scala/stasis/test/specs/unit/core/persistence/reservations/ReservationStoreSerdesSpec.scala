package stasis.test.specs.unit.core.persistence.reservations

import stasis.core.persistence.CrateStorageReservation
import stasis.core.persistence.reservations.ReservationStoreSerdes
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.core.persistence.Generators

class ReservationStoreSerdesSpec extends UnitSpec {
  "ReservationStoreSerdes" should "serialize and deserialize keys" in {
    val reservation = CrateStorageReservation.generateId()

    val serialized = ReservationStoreSerdes.serializeKey(reservation)
    val deserialized = ReservationStoreSerdes.deserializeKey(serialized)

    deserialized should be(reservation)
  }

  they should "serialize and deserialize values" in {
    val reservation = Generators.generateReservation

    val serialized = ReservationStoreSerdes.serializeValue(reservation)
    val deserialized = ReservationStoreSerdes.deserializeValue(serialized)

    deserialized should be(reservation)
  }
}
