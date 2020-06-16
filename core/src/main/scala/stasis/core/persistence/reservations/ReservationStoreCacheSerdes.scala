package stasis.core.persistence.reservations

import java.nio.charset.StandardCharsets
import java.util.UUID

import akka.util.ByteString
import stasis.core.packaging.Crate
import stasis.core.persistence.CrateStorageReservation
import stasis.core.persistence.backends.KeyValueBackend
import stasis.core.routing.Node

object ReservationStoreCacheSerdes extends KeyValueBackend.Serdes[(Crate.Id, Node.Id), CrateStorageReservation.Id] {
  private val separator: String = ":"

  override implicit def serializeKey: ((Crate.Id, Node.Id)) => String = {
    case (crate, node) => s"${crate.toString}$separator${node.toString}"
  }

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  override implicit def deserializeKey: String => (Crate.Id, Node.Id) =
    key =>
      key.split(separator).toList match {
        case crate :: node :: Nil => (UUID.fromString(crate), UUID.fromString(node))
        case _                    => throw new IllegalArgumentException(s"Unexpected reservation cache key provided: [$key]")
      }

  override implicit def serializeValue: CrateStorageReservation.Id => ByteString =
    reservation => ByteString(reservation.toString, StandardCharsets.UTF_8)

  override implicit def deserializeValue: ByteString => CrateStorageReservation.Id =
    reservation => UUID.fromString(reservation.decodeString(StandardCharsets.UTF_8))
}
