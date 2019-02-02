package stasis.core.networking.grpc

import java.util.UUID

import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import stasis.core.networking.exceptions.{CredentialsFailure, EndpointFailure}
import stasis.core.networking.grpc.internal.Client
import stasis.core.networking.{EndpointClient, EndpointCredentials}
import stasis.core.packaging.{Crate, Manifest}
import stasis.core.persistence.{CrateStorageRequest, CrateStorageReservation}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class GrpcEndpointClient(
  override protected val credentials: EndpointCredentials[GrpcEndpointAddress, GrpcCredentials]
)(implicit system: ActorSystem)
    extends EndpointClient[GrpcEndpointAddress, GrpcCredentials] {

  import internal.Implicits._

  private implicit val ec: ExecutionContext = system.dispatcher
  private implicit val mat: ActorMaterializer = ActorMaterializer()

  private val log = Logging(system, this.getClass.getName)

  override def push(
    address: GrpcEndpointAddress,
    manifest: Manifest,
    content: Source[ByteString, NotUsed]
  ): Future[Done] = {
    log.debug("Pushing to endpoint [{}] content with manifest [{}]", address.host, manifest)

    credentials.provide(address) match {
      case Some(endpointCredentials) =>
        val client = internal.Client(address)

        for {
          reservation <- reserveStorage(client, address, manifest, endpointCredentials)
          result <- pushCrate(client, address, manifest, endpointCredentials, reservation, content)
        } yield {
          result
        }

      case None =>
        val message =
          s"Push to endpoint [${address.host}] failed for crate [${manifest.crate}]; unable to retrieve credentials"
        log.error(message)
        Future.failed(CredentialsFailure(message))
    }
  }

  override def sink(
    address: GrpcEndpointAddress,
    manifest: Manifest
  ): Future[Sink[ByteString, Future[Done]]] = {
    log.debug("Building content sink for endpoint [{}] with manifest [{}]", address.host, manifest)

    credentials.provide(address) match {
      case Some(endpointCredentials) =>
        val client = internal.Client(address)

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

      case None =>
        val message =
          s"Push to endpoint [${address.host}] via sink failed for crate [${manifest.crate}]; unable to retrieve credentials"
        log.error(message)
        Future.failed(CredentialsFailure(message))
    }
  }

  override def pull(
    address: GrpcEndpointAddress,
    crate: Crate.Id
  ): Future[Option[Source[ByteString, NotUsed]]] = {
    log.debug("Pulling from endpoint [{}] crate with ID [{}]", address.host, crate)

    val client = internal.Client(address)

    credentials.provide(address) match {
      case Some(endpointCredentials) =>
        client
          .streamWithCredentials[proto.PullRequest, proto.PullChunk](_.pull(), endpointCredentials)
          .invoke(proto.PullRequest().withCrate(crate))
          .map(_.content: ByteString)
          .prefixAndTail(n = 1)
          .map(stream => (stream._1.toList, stream._2))
          .mapAsync(parallelism = 1) {
            case (head :: Nil, tail) =>
              log.info("Endpoint [{}] responded to pull with content for crate [{}]", address.host, crate)
              Future.successful(Some(Source.single(head) ++ tail))

            case _ =>
              log.warning("Endpoint [{}] responded to pull with no content for crate [{}]", address.host, crate)
              Future.successful(None)
          }
          .runWith(Sink.head)
          .recoverWith {
            case NonFatal(e) =>
              val exceptionMessage = e.getMessage.replaceAll("\r", "").replaceAll("\n", "; ")
              val message = s"Pull from endpoint [${address.host}] failed for crate [$crate]: [$exceptionMessage]"
              log.warning(message)
              Future.failed(EndpointFailure(message))
          }

      case None =>
        val message = s"Pull from endpoint [${address.host}] failed for crate [$crate]; unable to retrieve credentials"
        log.error(message)
        Future.failed(CredentialsFailure(message))
    }
  }

  override def discard(
    address: GrpcEndpointAddress,
    crate: Crate.Id
  ): Future[Boolean] = {
    log.debug("Discarding from endpoint [{}] crate with ID [{}]", address.host, crate)

    val client = internal.Client(address)

    credentials.provide(address) match {
      case Some(endpointCredentials) =>
        client
          .requestWithCredentials(_.discard(), endpointCredentials)
          .invoke(proto.DiscardRequest().withCrate(crate))
          .flatMap { response =>
            response.result.complete match {
              case Some(_) =>
                log.info("Endpoint [{}] completed discard for crate [{}]", address.host, crate)
                Future.successful(true)

              case None =>
                Future.failed(response.result.failure)
            }
          }
          .recoverWith {
            case NonFatal(e) =>
              val message = s"Discard from endpoint [${address.host}] failed for crate [$crate]: [${e.getMessage}]"
              log.warning(message)
              Future.successful(false)
          }

      case None =>
        val message =
          s"Discard from endpoint [${address.host}] failed for crate [$crate]; unable to retrieve credentials"
        log.error(message)
        Future.failed(CredentialsFailure(message))
    }
  }

  private def reserveStorage(
    client: Client,
    address: GrpcEndpointAddress,
    manifest: Manifest,
    endpointCredentials: GrpcCredentials
  ): Future[CrateStorageReservation.Id] = {
    val client = internal.Client(address)

    val storageRequest = CrateStorageRequest(manifest)

    client
      .requestWithCredentials(_.reserve(), endpointCredentials)
      .invoke(
        internal.Requests.Reserve.marshal(storageRequest)
      )
      .flatMap { response =>
        response.result.reservation match {
          case Some(reservation) =>
            log.info(
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
          log.warning(message)
          Future.failed(EndpointFailure(message))
      }
  }

  private def pushCrate(
    client: Client,
    address: GrpcEndpointAddress,
    manifest: Manifest,
    endpointCredentials: GrpcCredentials,
    reservation: CrateStorageReservation.Id,
    content: Source[ByteString, NotUsed]
  ): Future[Done] =
    client
      .requestWithCredentials(_.push(), endpointCredentials)
      .invoke(content.map(chunk => proto.PushChunk(Some(reservation), chunk)))
      .flatMap { response =>
        response.result.complete match {
          case Some(_) =>
            log.info("Endpoint [{}] completed push for crate [{}]", address.host, manifest.crate)
            Future.successful(Done)

          case None =>
            Future.failed(response.result.failure)
        }
      }
      .recoverWith {
        case NonFatal(e) =>
          val message =
            s"Push to endpoint [${address.host}] failed for crate [${manifest.crate}]: [${e.getMessage}]"
          log.warning(message)
          Future.failed(EndpointFailure(message))
      }
}
