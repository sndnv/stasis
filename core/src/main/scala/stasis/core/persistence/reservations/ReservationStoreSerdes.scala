package stasis.core.persistence.reservations

import java.nio.charset.StandardCharsets

import org.apache.pekko.util.ByteString
import play.api.libs.json.Json

import stasis.core.persistence.CrateStorageReservation
import stasis.core.persistence.backends.KeyValueBackend

object ReservationStoreSerdes extends KeyValueBackend.Serdes[CrateStorageReservation.Id, CrateStorageReservation] {
  import stasis.core.api.Formats._

  override implicit def serializeKey: CrateStorageReservation.Id => String =
    _.toString

  override implicit def deserializeKey: String => CrateStorageReservation.Id =
    java.util.UUID.fromString

  override implicit def serializeValue: CrateStorageReservation => ByteString =
    reservation => ByteString(Json.toJson(reservation).toString, StandardCharsets.UTF_8)

  override implicit def deserializeValue: ByteString => CrateStorageReservation =
    reservation => Json.parse(reservation.decodeString(StandardCharsets.UTF_8)).as[CrateStorageReservation]
}
