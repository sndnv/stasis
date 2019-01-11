package stasis.persistence.crates

import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import stasis.packaging.{Crate, Manifest}
import stasis.persistence.exceptions.ReservationFailure
import stasis.persistence.reservations.ReservationStore
import stasis.persistence.{CrateStorageRequest, CrateStorageReservation}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

import stasis.persistence.backends.StreamingBackend
import stasis.routing.Node

abstract class CrateStore(
  reservationStore: ReservationStore,
  reservationExpiration: FiniteDuration,
  val storeId: Node.Id
)(implicit val system: ActorSystem) { store =>
  private implicit val mat: ActorMaterializer = ActorMaterializer()
  private implicit val ec: ExecutionContext = system.dispatcher

  private val log = Logging(system, this.getClass.getName)

  def persist(manifest: Manifest, content: Source[ByteString, NotUsed]): Future[Done] = {
    log.debug("Persisting content for crate [{}] with manifest [{}]", manifest.crate, manifest)

    for {
      sink <- sink(manifest)
      result <- content.runWith(sink)
    } yield {
      log.debug("Persist completed for crate [{}]", manifest.crate)
      result
    }
  }

  def sink(manifest: Manifest): Future[Sink[ByteString, Future[Done]]] = {
    log.debug("Retrieving content sink for crate [{}] with manifest [{}]", manifest.crate, manifest)

    reservationStore.delete(manifest.crate, storeId).flatMap { reservationRemoved =>
      if (reservationRemoved) {
        log.debug("Reservation for crate [{}] removed", manifest.crate)
        directSink(manifest).map { sink =>
          log.debug("Content sink for crate [{}] removed", manifest.crate)
          sink
        }
      } else {
        val message = s"Failed to remove reservation for crate [${manifest.crate}]"
        log.error(message)
        Future.failed(ReservationFailure(message))
      }
    }
  }

  def retrieve(crate: Crate.Id): Future[Option[Source[ByteString, NotUsed]]] = {
    log.debug("Retrieving content source for crate [{}]", crate)
    directSource(crate)
  }

  def discard(crate: Crate.Id): Future[Boolean] = {
    log.debug("Discarding crate [{}]", crate)
    dropContent(crate).map { result =>
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
        isStorageAvailable(request).flatMap { storageAvailable =>
          if (storageAvailable) {
            val reservation = CrateStorageReservation(request, target = storeId, reservationExpiration)
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

  protected def directSink(manifest: Manifest): Future[Sink[ByteString, Future[Done]]]
  protected def directSource(crate: Crate.Id): Future[Option[Source[ByteString, NotUsed]]]
  protected def dropContent(crate: Crate.Id): Future[Boolean]
  protected def isStorageAvailable(request: CrateStorageRequest): Future[Boolean]
}

object CrateStore {
  def apply(
    backend: StreamingBackend[Crate.Id],
    reservationStore: ReservationStore,
    reservationExpiration: FiniteDuration,
    storeId: Node.Id
  )(implicit system: ActorSystem): CrateStore =
    new CrateStore(
      reservationStore,
      reservationExpiration,
      storeId
    ) {
      override protected def directSink(manifest: Manifest): Future[Sink[ByteString, Future[Done]]] =
        backend.sink(manifest.crate)

      override protected def directSource(crate: Crate.Id): Future[Option[Source[ByteString, NotUsed]]] =
        backend.source(crate)

      override protected def dropContent(crate: Crate.Id): Future[Boolean] =
        backend.delete(crate)

      override protected def isStorageAvailable(request: CrateStorageRequest): Future[Boolean] =
        backend.canStore(request.size * request.copies)
    }
}
