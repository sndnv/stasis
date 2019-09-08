package stasis.core.persistence.crates

import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import stasis.core.packaging.{Crate, Manifest}
import stasis.core.persistence.backends.StreamingBackend
import stasis.core.persistence.exceptions.ReservationFailure
import stasis.core.persistence.reservations.ReservationStore
import stasis.core.persistence.{CrateStorageRequest, CrateStorageReservation}
import stasis.core.routing.Node

import scala.concurrent.{ExecutionContext, Future}

abstract class CrateStore(
  reservationStore: ReservationStore,
  val storeId: Node.Id
)(implicit val system: ActorSystem) { store =>
  private implicit val mat: ActorMaterializer = ActorMaterializer()
  private implicit val ec: ExecutionContext = system.dispatcher

  private val log = Logging(system, this.getClass.getName)

  def backend: StreamingBackend

  def persist(manifest: Manifest, content: Source[ByteString, NotUsed]): Future[Done] = {
    log.debug("Persisting content for crate [{}] with manifest [{}]", manifest.crate, manifest)

    for {
      sink <- sink(manifest.crate)
      result <- content.runWith(sink)
    } yield {
      log.debug("Persist completed for crate [{}]", manifest.crate)
      result
    }
  }

  def sink(crate: Crate.Id): Future[Sink[ByteString, Future[Done]]] = {
    log.debug("Retrieving content sink for crate [{}]", crate)

    reservationStore.delete(crate, storeId).flatMap { reservationRemoved =>
      if (reservationRemoved) {
        log.debug("Reservation for crate [{}] removed", crate)
        backend.sink(crate).map { sink =>
          log.debug("Content sink for crate [{}] removed", crate)
          sink
        }
      } else {
        val message = s"Failed to remove reservation for crate [$crate]"
        log.error(message)
        Future.failed(ReservationFailure(message))
      }
    }
  }

  def retrieve(crate: Crate.Id): Future[Option[Source[ByteString, NotUsed]]] = {
    log.debug("Retrieving content source for crate [{}]", crate)
    backend.source(crate)
  }

  def discard(crate: Crate.Id): Future[Boolean] = {
    log.debug("Discarding crate [{}]", crate)
    backend.delete(crate).map { result =>
      if (result) {
        log.debug("Discarded crate [{}]", crate)
      } else {
        log.warning("Failed to discard crate [{}]; crate not found", crate)
      }

      result
    }
  }

  def reserve(request: CrateStorageRequest): Future[Option[CrateStorageReservation]] = {
    log.debug("Processing reservation request [{}] for crate [{}]", request, request.crate)

    reservationStore.existsFor(request.crate, storeId).flatMap { exists =>
      if (!exists) {
        backend.canStore(request.size * request.copies).flatMap { storageAvailable =>
          if (storageAvailable) {
            val reservation = CrateStorageReservation(request, target = storeId)
            reservationStore.put(reservation).map { _ =>
              Some(reservation)
            }
          } else {
            log.warning(
              "Storage request [{}] for crate [{}] cannot be fulfilled; storage not available",
              request,
              request.crate
            )
            Future.successful(None)
          }
        }
      } else {
        val message =
          s"Failed to process reservation request [$request]; reservation already exists for crate [${request.crate}]"
        log.error(message)
        Future.failed(ReservationFailure(message))
      }
    }
  }

  def view: CrateStoreView = new CrateStoreView {
    override def retrieve(crate: Crate.Id): Future[Option[Source[ByteString, NotUsed]]] = store.retrieve(crate)
  }
}

object CrateStore {
  def apply(
    streamingBackend: StreamingBackend,
    reservationStore: ReservationStore,
    storeId: Node.Id
  )(implicit system: ActorSystem): CrateStore =
    new CrateStore(
      reservationStore = reservationStore,
      storeId = storeId
    ) {
      override def backend: StreamingBackend = streamingBackend
    }
}
