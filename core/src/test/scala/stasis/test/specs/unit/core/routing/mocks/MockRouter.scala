package stasis.test.specs.unit.core.routing.mocks

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.apache.pekko.Done
import org.apache.pekko.NotUsed

import stasis.core.packaging.Crate
import stasis.core.packaging.Manifest
import stasis.core.persistence.crates.CrateStore
import stasis.core.persistence.exceptions.ReservationFailure
import stasis.core.persistence.reservations.ReservationStore
import stasis.core.persistence.CrateStorageRequest
import stasis.core.persistence.CrateStorageReservation
import stasis.core.routing.exceptions.DiscardFailure
import stasis.core.routing.Node
import stasis.core.routing.Router

class MockRouter(
  store: CrateStore,
  storeNode: Node.Id,
  reservationStore: ReservationStore,
  reservationDisabled: Boolean = false
)(implicit ec: ExecutionContext)
    extends Router {
  override def push(
    manifest: Manifest,
    content: Source[ByteString, NotUsed]
  ): Future[Done] = store.persist(manifest, content)

  override def pull(
    crate: Crate.Id
  ): Future[Option[Source[ByteString, NotUsed]]] = store.retrieve(crate)

  override def discard(crate: Crate.Id): Future[Done] =
    store.discard(crate).flatMap {
      case true  => Future.successful(Done)
      case false => Future.failed(DiscardFailure(s"Backing store could not find crate [$crate]"))
    }

  override def reserve(request: CrateStorageRequest): Future[Option[CrateStorageReservation]] =
    if (!reservationDisabled) {
      store.canStore(request).flatMap {
        case true =>
          val reservation = CrateStorageReservation(request, target = storeNode)
          reservationStore.put(reservation).map(_ => Some(reservation))

        case false =>
          Future.successful(None)
      }
    } else {
      Future.failed(ReservationFailure("[reservationDisabled] is set to [true]"))
    }
}
