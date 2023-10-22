package stasis.test.specs.unit.core.persistence.mocks

import org.apache.pekko.actor.typed.{ActorSystem, SpawnProtocol}
import org.apache.pekko.stream.scaladsl.{Flow, Keep, Sink, Source}
import org.apache.pekko.util.{ByteString, Timeout}
import org.apache.pekko.{Done, NotUsed}
import stasis.core.packaging.{Crate, Manifest}
import stasis.core.persistence.CrateStorageRequest
import stasis.core.persistence.backends.StreamingBackend
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.core.persistence.crates.CrateStore
import stasis.core.persistence.exceptions.PersistenceFailure
import stasis.core.telemetry.TelemetryContext

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class MockCrateStore(
  maxStorageSize: Option[Long] = None,
  persistDisabled: Boolean = false,
  sinkDisabled: Boolean = false,
  retrieveDisabled: Boolean = false,
  retrieveEmpty: Boolean = false,
  discardDisabled: Boolean = false
)(implicit system: ActorSystem[SpawnProtocol.Command], telemetry: TelemetryContext)
    extends CrateStore(backend = null /* not needed here; backend is overridden in this class */ ) {

  import MockCrateStore._

  private type StoreKey = Crate.Id
  private type StoreValue = ByteString

  private implicit val timeout: Timeout = 3.seconds
  private implicit val ec: ExecutionContext = system.executionContext

  private val store = MemoryBackend[StoreKey, StoreValue](name = s"mock-crate-store-${java.util.UUID.randomUUID()}")

  private val stats: Map[Statistic, AtomicInteger] = Map(
    Statistic.PersistCompleted -> new AtomicInteger(0),
    Statistic.PersistFailed -> new AtomicInteger(0),
    Statistic.RetrieveCompleted -> new AtomicInteger(0),
    Statistic.RetrieveEmpty -> new AtomicInteger(0),
    Statistic.RetrieveFailed -> new AtomicInteger(0),
    Statistic.DiscardCompleted -> new AtomicInteger(0),
    Statistic.DiscardFailed -> new AtomicInteger(0)
  )

  override protected def init(): Future[Done] = Future.successful(Done)

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

  override def sink(crate: Crate.Id): Future[Sink[StoreValue, Future[Done]]] =
    if (!sinkDisabled) {
      super.sink(crate)
    } else {
      stats(Statistic.PersistFailed).incrementAndGet()
      Future.failed(new PersistenceFailure("[sinkDisabled] is set to [true]"))
    }

  override def canStore(request: CrateStorageRequest): Future[Boolean] =
    maxStorageSize match {
      case Some(maxSize) => Future.successful(maxSize >= request.size)
      case None          => super.canStore(request)
    }

  def statistics: Map[Statistic, Int] = stats.view.mapValues(_.get()).toMap

  override val backend: StreamingBackend = new StreamingBackend {
    override val info: String = "MockCrateStore"

    override def init(): Future[Done] = Future.successful(Done)

    override def drop(): Future[Done] = Future.successful(Done)

    override def available(): Future[Boolean] = Future.successful(true)

    override def sink(key: StoreKey): Future[Sink[StoreValue, Future[Done]]] =
      Future.successful(
        Flow[ByteString]
          .fold(ByteString.empty)(_ concat _)
          .mapAsync(parallelism = 1) { data =>
            stats(Statistic.PersistCompleted).incrementAndGet()
            store.put(key, data)
          }
          .toMat(Sink.ignore)(Keep.right)
      )

    override def source(key: StoreKey): Future[Option[Source[StoreValue, NotUsed]]] =
      if (!retrieveDisabled) {
        if (!retrieveEmpty) {
          stats(Statistic.RetrieveCompleted).incrementAndGet()
          store.get(key).map(_.map(Source.single))
        } else {
          stats(Statistic.RetrieveEmpty).incrementAndGet()
          Future.successful(None)
        }
      } else {
        stats(Statistic.RetrieveFailed).incrementAndGet()
        Future.failed(new PersistenceFailure("[retrieveDisabled] is set to [true]"))
      }

    override def delete(key: StoreKey): Future[Boolean] =
      if (!discardDisabled) {
        store.delete(key).map {
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

    override def contains(key: StoreKey): Future[Boolean] =
      Future.successful(false)

    override def canStore(bytes: Long): Future[Boolean] =
      Future.successful(true)
  }
}

object MockCrateStore {
  sealed trait Statistic
  object Statistic {
    case object RetrieveCompleted extends Statistic
    case object RetrieveEmpty extends Statistic
    case object RetrieveFailed extends Statistic
    case object PersistCompleted extends Statistic
    case object PersistFailed extends Statistic
    case object DiscardCompleted extends Statistic
    case object DiscardFailed extends Statistic
  }
}
