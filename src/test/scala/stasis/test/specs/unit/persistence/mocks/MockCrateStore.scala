package stasis.test.specs.unit.persistence.mocks

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.util.{ByteString, Timeout}
import akka.{Done, NotUsed}
import stasis.packaging.Crate.Id
import stasis.packaging.{Crate, Manifest}
import stasis.persistence.backends.memory.MemoryBackend
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
  reservationDisabled: Boolean = false,
  reservationExpiration: FiniteDuration = 1.day,
  discardDisabled: Boolean = false
)(implicit system: ActorSystem[SpawnProtocol])
    extends CrateStore(reservationStore, reservationExpiration)(system.toUntyped) {

  import MockCrateStore._

  private type StoreKey = Crate.Id
  private type StoreValue = ByteString

  private implicit val timeout: Timeout = 3.seconds
  private implicit val ec: ExecutionContext = system.executionContext

  private val store =
    MemoryBackend.typed[StoreKey, StoreValue](name = s"mock-crate-store-${java.util.UUID.randomUUID()}")

  private val stats: Map[Statistic, AtomicInteger] = Map(
    Statistic.PersistCompleted -> new AtomicInteger(0),
    Statistic.PersistFailed -> new AtomicInteger(0),
    Statistic.RetrieveCompleted -> new AtomicInteger(0),
    Statistic.RetrieveEmpty -> new AtomicInteger(0),
    Statistic.RetrieveFailed -> new AtomicInteger(0),
    Statistic.ReserveCompleted -> new AtomicInteger(0),
    Statistic.ReserveLimited -> new AtomicInteger(0),
    Statistic.ReserveFailed -> new AtomicInteger(0),
    Statistic.DiscardCompleted -> new AtomicInteger(0),
    Statistic.DiscardFailed -> new AtomicInteger(0)
  )

  override def persist(
    manifest: Manifest,
    content: Source[ByteString, NotUsed]
  ): Future[Done] =
    if (!persistDisabled) {
      super.persist(manifest, content)
    } else {
      stats(Statistic.PersistFailed).incrementAndGet()
      Future.failed(new PersistenceFailure("[persistDisabled] is set to [true]"))
    }

  override def reserve(request: CrateStorageRequest): Future[Option[CrateStorageReservation]] =
    if (!reservationDisabled) {
      maxReservationSize match {
        case Some(size) if request.size > size =>
          stats(Statistic.ReserveLimited).incrementAndGet()
          Future.successful(None)

        case _ =>
          stats(Statistic.ReserveCompleted).incrementAndGet()
          super.reserve(request)
      }
    } else {
      stats(Statistic.ReserveFailed).incrementAndGet()
      Future.failed(new PersistenceFailure("[reservationDisabled] is set to [true]"))
    }

  def statistics: Map[Statistic, Int] = stats.mapValues(_.get())

  override protected def directSink(manifest: Manifest): Future[Sink[StoreValue, Future[Done]]] =
    Future.successful(
      Flow[ByteString]
        .fold(ByteString.empty) {
          case (folded, chunk) =>
            folded.concat(chunk)
        }
        .mapAsyncUnordered(parallelism = 1) { data =>
          stats(Statistic.PersistCompleted).incrementAndGet()
          store.put(manifest.crate, data)
        }
        .toMat(Sink.ignore)(Keep.right)
    )

  override protected def directSource(crate: Id): Future[Option[Source[StoreValue, NotUsed]]] =
    if (!retrieveDisabled) {
      if (!retrieveEmpty) {
        stats(Statistic.RetrieveCompleted).incrementAndGet()
        store.get(crate).map(_.map(Source.single))
      } else {
        stats(Statistic.RetrieveEmpty).incrementAndGet()
        Future.successful(None)
      }
    } else {
      stats(Statistic.RetrieveFailed).incrementAndGet()
      Future.failed(new PersistenceFailure("[retrieveDisabled] is set to [true]"))
    }

  override protected def dropContent(crate: Id): Future[Boolean] =
    if (!discardDisabled) {
      store.delete(crate).map {
        case true =>
          stats(Statistic.DiscardCompleted).incrementAndGet()
          true

        case false =>
          stats(Statistic.DiscardFailed).incrementAndGet()
          false
      }
    } else {
      stats(Statistic.DiscardFailed).incrementAndGet()
      Future.successful(false)
    }

  override protected def isStorageAvailable(request: CrateStorageRequest): Future[Boolean] =
    Future.successful(true)
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
    case object DiscardCompleted extends Statistic
    case object DiscardFailed extends Statistic
  }
}
