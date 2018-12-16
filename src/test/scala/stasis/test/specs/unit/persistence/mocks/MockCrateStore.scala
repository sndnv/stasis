package stasis.test.specs.unit.persistence.mocks

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.Scheduler
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
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
  persistDisabled: Boolean = false,
  retrieveDisabled: Boolean = false,
  retrieveEmpty: Boolean = false,
  reservationDisabled: Boolean = false
)(implicit system: ActorSystem[SpawnProtocol])
    extends CrateStore {

  import MockCrateStore._

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

  private val stats: Map[Statistic, AtomicInteger] = Map(
    Statistic.PersistCompleted -> new AtomicInteger(0),
    Statistic.PersistFailed -> new AtomicInteger(0),
    Statistic.RetrieveCompleted -> new AtomicInteger(0),
    Statistic.RetrieveEmpty -> new AtomicInteger(0),
    Statistic.RetrieveFailed -> new AtomicInteger(0),
    Statistic.ReserveCompleted -> new AtomicInteger(0),
    Statistic.ReserveLimited -> new AtomicInteger(0),
    Statistic.ReserveFailed -> new AtomicInteger(0)
  )

  override def persist(
    manifest: Manifest,
    content: Source[ByteString, NotUsed]
  ): Future[Done] =
    if (!persistDisabled) {
      stats(Statistic.PersistCompleted).incrementAndGet()
      content
        .runFold(ByteString.empty) {
          case (folded, chunk) =>
            folded.concat(chunk)
        }
        .flatMap { data =>
          storeRef.flatMap(_ ? (ref => MapStoreActor.Put(manifest.crate, data, ref)))
        }
    } else {
      stats(Statistic.PersistFailed).incrementAndGet()
      Future.failed(new PersistenceFailure("[persistDisabled] is set to [true]"))
    }

  override def sink(manifest: Manifest): Future[Sink[ByteString, Future[Done]]] =
    Future.successful(
      Flow[ByteString]
        .fold(ByteString.empty) {
          case (folded, chunk) =>
            folded.concat(chunk)
        }
        .mapAsyncUnordered(parallelism = 1) { data =>
          stats(Statistic.PersistCompleted).incrementAndGet()
          val result: Future[Done] = storeRef.flatMap(_ ? (ref => MapStoreActor.Put(manifest.crate, data, ref)))
          result
        }
        .toMat(Sink.ignore)(Keep.right)
    )

  override def retrieve(
    crate: Crate.Id
  ): Future[Option[Source[ByteString, NotUsed]]] =
    if (!retrieveDisabled) {
      if (!retrieveEmpty) {
        stats(Statistic.RetrieveCompleted).incrementAndGet()
        val result: Future[Option[ByteString]] = storeRef.flatMap(_ ? (ref => MapStoreActor.Get(crate, ref)))
        result.map(_.map(Source.single))
      } else {
        stats(Statistic.RetrieveEmpty).incrementAndGet()
        Future.successful(None)
      }
    } else {
      stats(Statistic.RetrieveFailed).incrementAndGet()
      Future.failed(new PersistenceFailure("[retrieveDisabled] is set to [true]"))
    }

  override def reserve(request: CrateStorageRequest): Future[Option[CrateStorageReservation]] =
    if (!reservationDisabled) {
      maxReservationSize match {
        case Some(size) if request.size > size =>
          stats(Statistic.ReserveLimited).incrementAndGet()
          Future.successful(None)

        case _ =>
          val reservation = CrateStorageReservation(
            id = CrateStorageReservation.generateId(),
            size = request.size,
            copies = request.copies,
            retention = request.retention,
            expiration = 1.day
          )

          stats(Statistic.ReserveCompleted).incrementAndGet()
          reservationStore.put(reservation).map { _ =>
            Some(reservation)
          }
      }
    } else {
      stats(Statistic.ReserveFailed).incrementAndGet()
      Future.failed(new PersistenceFailure("[reservationDisabled] is set to [true]"))
    }

  def statistics: Map[Statistic, Int] = stats.mapValues(_.get())
}

object MockCrateStore {
  sealed trait Statistic
  object Statistic {
    case object ReserveCompleted extends Statistic
    case object ReserveLimited extends Statistic
    case object ReserveFailed extends Statistic
    case object RetrieveCompleted extends Statistic
    case object RetrieveEmpty extends Statistic
    case object RetrieveFailed extends Statistic
    case object PersistCompleted extends Statistic
    case object PersistFailed extends Statistic
  }
}
