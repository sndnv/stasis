package stasis.test.specs.unit.networking.mocks

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.Scheduler
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.util.{ByteString, Timeout}
import akka.{Done, NotUsed}
import stasis.networking.http.{HttpEndpointAddress, HttpEndpointClient}
import stasis.packaging.{Crate, Manifest}
import stasis.test.specs.unit.persistence.mocks.MapStoreActor

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class MockEndpointClient(
  pushFailureAddresses: Map[HttpEndpointAddress, Exception] = Map.empty,
  pullFailureAddresses: Map[HttpEndpointAddress, Exception] = Map.empty,
  pullEmptyAddresses: Seq[HttpEndpointAddress] = Seq.empty
)(implicit system: ActorSystem[SpawnProtocol])
    extends HttpEndpointClient((_: HttpEndpointAddress) => None)(system.toUntyped) {

  import MockEndpointClient._

  private implicit val timeout: Timeout = 3.seconds
  private implicit val scheduler: Scheduler = system.scheduler
  private implicit val mat: ActorMaterializer = ActorMaterializer()(system.toUntyped)
  private implicit val ec: ExecutionContext = system.executionContext

  private val clientId = java.util.UUID.randomUUID()

  private val storeRef =
    system ? SpawnProtocol.Spawn(
      MapStoreActor.store(Map.empty[StoreKey, StoreValue]),
      s"mock-endpoint-store-$clientId"
    )

  private val stats: Map[Statistic, AtomicInteger] = Map(
    Statistic.PushCompleted -> new AtomicInteger(0),
    Statistic.PushFailed -> new AtomicInteger(0),
    Statistic.PullCompletedWithData -> new AtomicInteger(0),
    Statistic.PullCompletedEmpty -> new AtomicInteger(0),
    Statistic.PullFailed -> new AtomicInteger(0)
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
            storeRef.flatMap(_ ? (ref => MapStoreActor.Put((address, manifest.crate), (data, manifest.copies), ref)))
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
              val result: Future[Done] =
                storeRef.flatMap(
                  _ ? (ref => MapStoreActor.Put((address, manifest.crate), (data, manifest.copies), ref))
                )
              result
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
          val storeResult: Future[Option[(ByteString, Int)]] =
            storeRef.flatMap(_ ? (ref => MapStoreActor.Get((address, crate), ref)))

          storeResult.map(_.map(result => Source.single(result._1)))
        } else {
          stats(Statistic.PullCompletedEmpty).incrementAndGet()
          Future.successful(None)
        }
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

  private def storeData: Future[Map[StoreKey, StoreValue]] =
    storeRef.flatMap(_ ? (ref => MapStoreActor.GetAll(ref)))
}

object MockEndpointClient {
  private type StoreKey = (HttpEndpointAddress, Crate.Id)
  private type StoreValue = (ByteString, Int)

  sealed trait Statistic
  object Statistic {
    case object PullCompletedWithData extends Statistic
    case object PullCompletedEmpty extends Statistic
    case object PullFailed extends Statistic
    case object PushCompleted extends Statistic
    case object PushFailed extends Statistic
  }
}
