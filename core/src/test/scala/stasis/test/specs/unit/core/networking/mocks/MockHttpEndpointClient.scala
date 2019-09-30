package stasis.test.specs.unit.core.networking.mocks

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.util.{ByteString, Timeout}
import akka.{Done, NotUsed}
import stasis.core.networking.http.{HttpEndpointAddress, HttpEndpointClient}
import stasis.core.packaging.{Crate, Manifest}
import stasis.core.persistence.backends.memory.MemoryBackend

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class MockHttpEndpointClient(
  pushFailureAddresses: Map[HttpEndpointAddress, Exception] = Map.empty,
  pullFailureAddresses: Map[HttpEndpointAddress, Exception] = Map.empty,
  discardFailureAddresses: Map[HttpEndpointAddress, Exception] = Map.empty,
  pullEmptyAddresses: Seq[HttpEndpointAddress] = Seq.empty
)(implicit system: ActorSystem[SpawnProtocol])
    extends HttpEndpointClient(
      (_: HttpEndpointAddress) => Future.failed(new RuntimeException("No credentials available")),
      context = None
    ) {

  import MockHttpEndpointClient._

  private implicit val timeout: Timeout = 3.seconds
  private implicit val mat: ActorMaterializer = ActorMaterializer()(system.toUntyped)
  private implicit val ec: ExecutionContext = system.executionContext

  private val store = MemoryBackend.typed[StoreKey, StoreValue](
    name = s"mock-endpoint-store-${java.util.UUID.randomUUID()}"
  )

  private val stats: Map[Statistic, AtomicInteger] = Map(
    Statistic.PushCompleted -> new AtomicInteger(0),
    Statistic.PushFailed -> new AtomicInteger(0),
    Statistic.PullCompletedWithData -> new AtomicInteger(0),
    Statistic.PullCompletedEmpty -> new AtomicInteger(0),
    Statistic.PullFailed -> new AtomicInteger(0),
    Statistic.DiscardCompleted -> new AtomicInteger(0),
    Statistic.DiscardFailed -> new AtomicInteger(0)
  )

  override def push(
    address: HttpEndpointAddress,
    manifest: Manifest,
    content: Source[ByteString, NotUsed]
  ): Future[Done] =
    pushFailureAddresses.get(address) match {
      case Some(pushFailure) =>
        stats(Statistic.PushFailed).incrementAndGet()
        Future.failed(pushFailure)

      case None =>
        stats(Statistic.PushCompleted).incrementAndGet()
        content
          .runFold(ByteString.empty) {
            case (folded, chunk) =>
              folded.concat(chunk)
          }
          .flatMap { data =>
            store.put((address, manifest.crate), (data, manifest.copies))
          }
    }

  override def sink(address: HttpEndpointAddress, manifest: Manifest): Future[Sink[ByteString, Future[Done]]] =
    pushFailureAddresses.get(address) match {
      case Some(pushFailure) =>
        stats(Statistic.PushFailed).incrementAndGet()
        Future.failed(pushFailure)

      case None =>
        Future.successful(
          Flow[ByteString]
            .fold(ByteString.empty) {
              case (folded, chunk) =>
                folded.concat(chunk)
            }
            .mapAsyncUnordered(parallelism = 1) { data =>
              stats(Statistic.PushCompleted).incrementAndGet()
              store.put((address, manifest.crate), (data, manifest.copies))
            }
            .toMat(Sink.ignore)(Keep.right)
        )
    }

  override def pull(
    address: HttpEndpointAddress,
    crate: Crate.Id
  ): Future[Option[Source[ByteString, NotUsed]]] =
    pullFailureAddresses.get(address) match {
      case Some(pullFailure) =>
        stats(Statistic.PullFailed).incrementAndGet()
        Future.failed(pullFailure)

      case None =>
        if (!pullEmptyAddresses.contains(address)) {
          stats(Statistic.PullCompletedWithData).incrementAndGet()
          store.get((address, crate)).map(_.map(result => Source.single(result._1)))
        } else {
          stats(Statistic.PullCompletedEmpty).incrementAndGet()
          Future.successful(None)
        }
    }

  override def discard(address: HttpEndpointAddress, crate: Crate.Id): Future[Boolean] =
    discardFailureAddresses.get(address) match {
      case Some(discardFailure) =>
        stats(Statistic.DiscardFailed).incrementAndGet()
        Future.failed(discardFailure)

      case None =>
        stats(Statistic.DiscardCompleted).incrementAndGet()
        Future.successful(true)
    }

  def crateCopies(crate: Crate.Id): Future[Int] =
    storeData.map { map =>
      map.collect {
        case ((_, currentCrate), (_, copies)) if currentCrate == crate =>
          copies
      }.sum
    }

  def crateNodes(crate: Crate.Id): Future[Int] =
    storeData.map { map =>
      map.keys
        .collect {
          case (address, currentCrate) if currentCrate == crate =>
            address
        }
        .toSeq
        .distinct
        .size
    }

  def statistics: Map[Statistic, Int] = stats.mapValues(_.get())

  private def storeData: Future[Map[StoreKey, StoreValue]] = store.entries
}

object MockHttpEndpointClient {
  private type StoreKey = (HttpEndpointAddress, Crate.Id)
  private type StoreValue = (ByteString, Int)

  sealed trait Statistic
  object Statistic {
    case object PullCompletedWithData extends Statistic
    case object PullCompletedEmpty extends Statistic
    case object PullFailed extends Statistic
    case object PushCompleted extends Statistic
    case object PushFailed extends Statistic
    case object DiscardCompleted extends Statistic
    case object DiscardFailed extends Statistic
  }
}
