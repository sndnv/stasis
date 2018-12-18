package stasis.persistence.crates

import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import stasis.packaging.Crate.Id
import stasis.packaging.{Crate, Manifest}
import stasis.persistence.exceptions.ReservationFailure
import stasis.persistence.reservations.ReservationStore
import stasis.persistence.{CrateStorageRequest, CrateStorageReservation}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

abstract class CrateStore(
  reservationStore: ReservationStore,
  reservationExpiration: FiniteDuration
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

    reservationStore.discard(manifest.crate).flatMap { reservationDiscarded =>
      if (reservationDiscarded) {
        log.debug("Reservation for crate [{}] discarded", manifest.crate)
        directSink(manifest).map { sink =>
          log.debug("Content sink for crate [{}] retrieved", manifest.crate)
          sink
        }
      } else {
        val message = s"Failed to discard reservation for crate [${manifest.crate}]"
        log.error(message)
        Future.failed(ReservationFailure(message))
      }
    }
  }

  def retrieve(crate: Crate.Id): Future[Option[Source[ByteString, NotUsed]]] = {
    log.debug("Retrieving content source for crate [{}]", crate)
    directSource(crate)
  }

  def reserve(request: CrateStorageRequest): Future[Option[CrateStorageReservation]] = {
    log.debug("Processing reservation request [{}] for crate", request, request.crate)

    reservationStore.existsFor(request.crate).flatMap { exists =>
      if (!exists) {
        isStorageAvailable(request).flatMap { storageAvailable =>
          if (storageAvailable) {
            val reservation = CrateStorageReservation(request, reservationExpiration)
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
    override def retrieve(crate: Id): Future[Option[Source[ByteString, NotUsed]]] = store.retrieve(crate)
  }

  protected def directSink(manifest: Manifest): Future[Sink[ByteString, Future[Done]]]
  protected def directSource(crate: Crate.Id): Future[Option[Source[ByteString, NotUsed]]]
  protected def isStorageAvailable(request: CrateStorageRequest): Future[Boolean]
}
