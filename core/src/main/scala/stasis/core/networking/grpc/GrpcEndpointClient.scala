package stasis.core.networking.grpc

import java.util.UUID

import akka.actor.typed.scaladsl.LoggerOps
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.http.scaladsl.HttpsConnectionContext
import akka.http.scaladsl.model.headers.HttpCredentials
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import org.slf4j.LoggerFactory
import stasis.core.networking.EndpointClient
import stasis.core.networking.exceptions.{CredentialsFailure, EndpointFailure}
import stasis.core.networking.grpc.internal.Client
import stasis.core.packaging.{Crate, Manifest}
import stasis.core.persistence.{CrateStorageRequest, CrateStorageReservation}
import stasis.core.security.NodeCredentialsProvider

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class GrpcEndpointClient(
  override protected val credentials: NodeCredentialsProvider[GrpcEndpointAddress, HttpCredentials],
  context: Option[HttpsConnectionContext]
)(implicit system: ActorSystem[SpawnProtocol.Command])
    extends EndpointClient[GrpcEndpointAddress, HttpCredentials] {

  import internal.Implicits._

  private implicit val ec: ExecutionContext = system.executionContext

  private val log = LoggerFactory.getLogger(this.getClass.getName)

  override def push(
    address: GrpcEndpointAddress,
    manifest: Manifest,
    content: Source[ByteString, NotUsed]
  ): Future[Done] = {
    log.debugN("Pushing to endpoint [{}] content with manifest [{}]", address.host, manifest)

    credentials
      .provide(address)
      .recoverWith {
        case NonFatal(e) =>
          val message =
            s"Push to endpoint [${address.host}] failed for crate [${manifest.crate}];" +
              s" unable to retrieve credentials: [${e.getMessage}]"
          log.error(message)
          Future.failed(CredentialsFailure(message))
      }
      .flatMap { endpointCredentials =>
        val client = internal.Client(address, context)

        for {
          reservation <- reserveStorage(client, address, manifest, endpointCredentials)
          result <- pushCrate(client, address, manifest, endpointCredentials, reservation, content)
        } yield {
          result
        }
      }
  }

  override def sink(
    address: GrpcEndpointAddress,
    manifest: Manifest
  ): Future[Sink[ByteString, Future[Done]]] = {
    log.debugN("Building content sink for endpoint [{}] with manifest [{}]", address.host, manifest)

    credentials
      .provide(address)
      .recoverWith {
        case NonFatal(e) =>
          val message =
            s"Push to endpoint [${address.host}] via sink failed for crate [${manifest.crate}];" +
              s" unable to retrieve credentials: [${e.getMessage}]"
          log.error(message)
          Future.failed(CredentialsFailure(message))
      }
      .flatMap { endpointCredentials =>
        val client = internal.Client(address, context)

        val (sink, content) = Source
          .asSubscriber[ByteString]
          .toMat(Sink.asPublisher[ByteString](fanout = false))(Keep.both)
          .mapMaterializedValue {
            case (subscriber, publisher) =>
              (Sink.fromSubscriber(subscriber), Source.fromPublisher(publisher))
          }
          .run()

        reserveStorage(client, address, manifest, endpointCredentials).map { reservation =>
          val _ = pushCrate(client, address, manifest, endpointCredentials, reservation, content)
          sink.mapMaterializedValue(_ => Future.successful(Done))
        }
      }
  }

  override def pull(
    address: GrpcEndpointAddress,
    crate: Crate.Id
  ): Future[Option[Source[ByteString, NotUsed]]] = {
    log.debugN("Pulling from endpoint [{}] crate with ID [{}]", address.host, crate)

    val client = internal.Client(address, context)

    credentials
      .provide(address)
      .recoverWith {
        case NonFatal(e) =>
          val message =
            s"Pull from endpoint [${address.host}] failed for crate [$crate];" +
              s" unable to retrieve credentials: [${e.getMessage}]"
          log.error(message)
          Future.failed(CredentialsFailure(message))
      }
      .flatMap { endpointCredentials =>
        client
          .streamWithCredentials[proto.PullRequest, proto.PullChunk](_.pull(), endpointCredentials)
          .invoke(proto.PullRequest().withCrate(crate))
          .map(_.content: ByteString)
          .prefixAndTail(n = 1)
          .map(stream => (stream._1.toList, stream._2))
          .mapAsync(parallelism = 1) {
            case (head :: Nil, tail) =>
              log.debugN("Endpoint [{}] responded to pull with content for crate [{}]", address.host, crate)
              Future.successful(Some(Source.single(head) ++ tail))

            case _ =>
              log.warnN("Endpoint [{}] responded to pull with no content for crate [{}]", address.host, crate)
              Future.successful(None)
          }
          .runWith(Sink.head)
          .recoverWith {
            case NonFatal(e) =>
              val exceptionMessage = e.getMessage.replaceAll("\r", "").replaceAll("\n", "; ")
              val message = s"Pull from endpoint [${address.host}] failed for crate [$crate]: [$exceptionMessage]"
              log.warn(message)
              Future.failed(EndpointFailure(message))
          }
      }
  }

  override def discard(
    address: GrpcEndpointAddress,
    crate: Crate.Id
  ): Future[Boolean] = {
    log.debugN("Discarding from endpoint [{}] crate with ID [{}]", address.host, crate)

    val client = internal.Client(address, context)

    credentials
      .provide(address)
      .recoverWith {
        case NonFatal(e) =>
          val message =
            s"Discard from endpoint [${address.host}] failed for crate [$crate];" +
              s" unable to retrieve credentials: [${e.getMessage}]"
          log.error(message)
          Future.failed(CredentialsFailure(message))
      }
      .flatMap { endpointCredentials =>
        client
          .requestWithCredentials(_.discard(), endpointCredentials)
          .invoke(proto.DiscardRequest().withCrate(crate))
          .flatMap { response =>
            response.result.complete match {
              case Some(_) =>
                log.debugN("Endpoint [{}] completed discard for crate [{}]", address.host, crate)
                Future.successful(true)

              case None =>
                Future.failed(response.result.failure)
            }
          }
          .recoverWith {
            case NonFatal(e) =>
              val message = s"Discard from endpoint [${address.host}] failed for crate [$crate]: [${e.getMessage}]"
              log.warnN(message)
              Future.successful(false)
          }
      }
  }

  private def reserveStorage(
    client: Client,
    address: GrpcEndpointAddress,
    manifest: Manifest,
    endpointCredentials: HttpCredentials
  ): Future[CrateStorageReservation.Id] = {
    val client = internal.Client(address, context)

    val storageRequest = CrateStorageRequest(manifest)

    client
      .requestWithCredentials(_.reserve(), endpointCredentials)
      .invoke(
        internal.Requests.Reserve.marshal(storageRequest)
      )
      .flatMap { response =>
        response.result.reservation match {
          case Some(reservation) =>
            log.debug(
              "Endpoint [{}] responded to storage request [{}] with: [{}]",
              address.host,
              storageRequest,
              reservation
            )
            Future.successful(reservation: UUID)

          case None =>
            Future.failed(response.result.failure)
        }
      }
      .recoverWith {
        case NonFatal(e) =>
          val message =
            s"Reservation on endpoint [${address.host}] failed for crate [${manifest.crate}]: [${e.getMessage}]"
          log.warn(message)
          Future.failed(EndpointFailure(message))
      }
  }

  private def pushCrate(
    client: Client,
    address: GrpcEndpointAddress,
    manifest: Manifest,
    endpointCredentials: HttpCredentials,
    reservation: CrateStorageReservation.Id,
    content: Source[ByteString, NotUsed]
  ): Future[Done] =
    client
      .requestWithCredentials(_.push(), endpointCredentials)
      .invoke(content.map(chunk => proto.PushChunk(Some(reservation), chunk)))
      .flatMap { response =>
        response.result.complete match {
          case Some(_) =>
            log.debugN("Endpoint [{}] completed push for crate [{}]", address.host, manifest.crate)
            Future.successful(Done)

          case None =>
            Future.failed(response.result.failure)
        }
      }
      .recoverWith {
        case NonFatal(e) =>
          val message =
            s"Push to endpoint [${address.host}] failed for crate [${manifest.crate}]: [${e.getMessage}]"
          log.warn(message)
          Future.failed(EndpointFailure(message))
      }
}

object GrpcEndpointClient {
  def apply(
    credentials: NodeCredentialsProvider[GrpcEndpointAddress, HttpCredentials]
  )(implicit system: ActorSystem[SpawnProtocol.Command]): GrpcEndpointClient =
    new GrpcEndpointClient(
      credentials = credentials,
      context = None
    )

  def apply(
    credentials: NodeCredentialsProvider[GrpcEndpointAddress, HttpCredentials],
    context: HttpsConnectionContext
  )(implicit system: ActorSystem[SpawnProtocol.Command]): GrpcEndpointClient =
    new GrpcEndpointClient(
      credentials = credentials,
      context = Some(context)
    )
}
