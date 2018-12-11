package stasis.test.specs.unit.persistence.mocks

import akka.actor.Scheduler
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.{ByteString, Timeout}
import akka.{Done, NotUsed}
import stasis.packaging.{Crate, Manifest}
import stasis.persistence.crates.CrateStore
import stasis.persistence.exceptions.PersistenceFailure
import stasis.persistence.reservations.ReservationStore
import stasis.persistence.{CrateStorageRequest, CrateStorageReservation}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class MockCrateStore(
  reservationStore: ReservationStore,
  maxReservationSize: Option[Long] = None,
  persistDisabled: Boolean = false
)(implicit system: ActorSystem[SpawnProtocol])
    extends CrateStore {
  private type StoreKey = Crate.Id
  private type StoreValue = ByteString

  private implicit val timeout: Timeout = 3.seconds
  private implicit val scheduler: Scheduler = system.scheduler
  private implicit val mat: ActorMaterializer = ActorMaterializer()(system.toUntyped)
  private implicit val ec: ExecutionContext = system.executionContext

  private val storeRef =
    system ? SpawnProtocol.Spawn(
      MapStoreActor.store(Map.empty[StoreKey, StoreValue]),
      s"mock-crate-store-${java.util.UUID.randomUUID()}"
    )

  override def persist(
    manifest: Manifest,
    content: Source[ByteString, NotUsed]
  ): Future[Done] =
    if (!persistDisabled) {
      content
        .runFold(ByteString.empty) {
          case (folded, chunk) =>
            folded.concat(chunk)
        }
        .flatMap { data =>
          storeRef.flatMap(_ ? (ref => MapStoreActor.Put(manifest.crate, data, ref)))
        }
    } else {
      Future.failed(new PersistenceFailure("[persistDisabled] is set to [true]"))
    }

  override def retrieve(
    crate: Crate.Id
  ): Future[Option[Source[ByteString, NotUsed]]] = {
    val result: Future[Option[ByteString]] = storeRef.flatMap(_ ? (ref => MapStoreActor.Get(crate, ref)))
    result.map(_.map(Source.single))
  }

  override def reserve(request: CrateStorageRequest): Future[Option[CrateStorageReservation]] =
    maxReservationSize match {
      case Some(size) if request.size > size =>
        Future.successful(None)

      case _ =>
        val reservation = CrateStorageReservation(
          id = CrateStorageReservation.generateId(),
          size = request.size,
          copies = request.copies,
          retention = request.retention,
          expiration = 1.day
        )

        reservationStore.put(reservation).map { _ =>
          Some(reservation)
        }
    }
}
