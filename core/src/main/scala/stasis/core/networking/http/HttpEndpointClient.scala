package stasis.core.networking.http

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.HttpCredentials
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import stasis.core.networking.EndpointClient
import stasis.core.networking.exceptions.{CredentialsFailure, EndpointFailure, ReservationFailure}
import stasis.core.packaging.{Crate, Manifest}
import stasis.core.persistence.{CrateStorageRequest, CrateStorageReservation}
import stasis.core.security.NodeCredentialsProvider

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@SuppressWarnings(Array("org.wartremover.warts.Any"))
class HttpEndpointClient(
  override protected val credentials: NodeCredentialsProvider[HttpEndpointAddress, HttpCredentials]
)(implicit val system: ActorSystem)
    extends EndpointClient[HttpEndpointAddress, HttpCredentials] {

  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.core.api.Formats._

  private implicit val mat: ActorMaterializer = ActorMaterializer()
  private implicit val ec: ExecutionContext = system.dispatcher

  private val log = Logging(system, this.getClass.getName)

  override def push(
    address: HttpEndpointAddress,
    manifest: Manifest,
    content: Source[ByteString, NotUsed]
  ): Future[Done] = {
    log.debug("Pushing to endpoint [{}] content with manifest [{}]", address.uri, manifest)

    credentials
      .provide(address)
      .recoverWith {
        case NonFatal(e) =>
          val message =
            s"Push to endpoint [${address.uri}] failed for crate [${manifest.crate}];" +
              s" unable to retrieve credentials: [${e.getMessage}]"
          log.error(message)
          Future.failed(CredentialsFailure(message))
      }
      .flatMap { endpointCredentials =>
        for {
          reservation <- reserveStorage(address, manifest, endpointCredentials)
          result <- pushCrate(address, manifest, content, reservation, endpointCredentials)
        } yield {
          result
        }
      }

  }

  override def sink(address: HttpEndpointAddress, manifest: Manifest): Future[Sink[ByteString, Future[Done]]] = {
    log.debug("Building content sink for endpoint [{}] with manifest [{}]", address.uri, manifest)

    val (sink, content) = Source
      .asSubscriber[ByteString]
      .toMat(Sink.asPublisher[ByteString](fanout = false))(Keep.both)
      .mapMaterializedValue {
        case (subscriber, publisher) =>
          (Sink.fromSubscriber(subscriber), Source.fromPublisher(publisher))
      }
      .run()

    credentials
      .provide(address)
      .recoverWith {
        case NonFatal(e) =>
          val message =
            s"Push to endpoint [${address.uri}] via sink failed for crate [${manifest.crate}];" +
              s" unable to retrieve credentials: [${e.getMessage}]"
          log.error(message)
          Future.failed(CredentialsFailure(message))
      }
      .flatMap { endpointCredentials =>
        reserveStorage(address, manifest, endpointCredentials).map { reservation =>
          val _ = pushCrate(address, manifest, content, reservation, endpointCredentials)
          sink.mapMaterializedValue(_ => Future.successful(Done))
        }
      }

  }

  override def pull(
    address: HttpEndpointAddress,
    crate: Crate.Id
  ): Future[Option[Source[ByteString, NotUsed]]] = {
    log.debug("Pulling from endpoint [{}] crate with ID [{}]", address.uri, crate)

    credentials
      .provide(address)
      .recoverWith {
        case NonFatal(e) =>
          val message =
            s"Pull from endpoint [${address.uri}] failed for crate [$crate];" +
              s" unable to retrieve credentials: [${e.getMessage}]"
          log.error(message)
          Future.failed(CredentialsFailure(message))
      }
      .flatMap { endpointCredentials =>
        pullCrate(address, crate, endpointCredentials)
      }

  }

  override def discard(address: HttpEndpointAddress, crate: Crate.Id): Future[Boolean] = {
    log.debug("Discarding from endpoint [{}] crate with ID [{}]", address.uri, crate)

    credentials
      .provide(address)
      .recoverWith {
        case NonFatal(e) =>
          val message =
            s"Discard from endpoint [${address.uri}] failed for crate [$crate];" +
              s" unable to retrieve credentials: [${e.getMessage}]"
          log.error(message)
          Future.failed(CredentialsFailure(message))
      }
      .flatMap { endpointCredentials =>
        discardCrate(address, crate, endpointCredentials)
      }
  }

  private def reserveStorage(
    address: HttpEndpointAddress,
    manifest: Manifest,
    endpointCredentials: HttpCredentials
  ): Future[CrateStorageReservation] = {
    val storageRequest = CrateStorageRequest(manifest)

    Marshal(storageRequest).to[RequestEntity].flatMap { requestEntity =>
      Http()
        .singleRequest(
          request = HttpRequest(
            method = HttpMethods.PUT,
            uri = s"${address.uri}/reservations",
            entity = requestEntity
          ).addCredentials(endpointCredentials)
        )
        .flatMap {
          case HttpResponse(status, _, entity, _) =>
            status match {
              case StatusCodes.OK =>
                Unmarshal(entity).to[CrateStorageReservation].map { reservation =>
                  log.info(
                    "Endpoint [{}] responded to storage request [{}] with: [{}]",
                    address.uri,
                    storageRequest,
                    reservation
                  )

                  reservation
                }

              case StatusCodes.InsufficientStorage =>
                val message =
                  s"Endpoint [${address.uri}] was unable to reserve enough storage for request [$storageRequest]"
                log.warning(message)
                Future.failed(ReservationFailure(message))

              case _ =>
                val message =
                  s"Endpoint [${address.uri}] responded to storage request with unexpected status: [${status.value}]"
                log.warning(message)
                Future.failed(EndpointFailure(message))
            }
        }
    }
  }

  private def pushCrate(
    address: HttpEndpointAddress,
    manifest: Manifest,
    content: Source[ByteString, NotUsed],
    reservation: CrateStorageReservation,
    endpointCredentials: HttpCredentials
  ): Future[Done] =
    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.PUT,
          uri = s"${address.uri}/crates/${manifest.crate}?reservation=${reservation.id}",
          entity = HttpEntity(ContentTypes.`application/octet-stream`, content)
        ).addCredentials(endpointCredentials)
      )
      .flatMap { response =>
        response.status match {
          case StatusCodes.OK =>
            log.info("Endpoint [{}] responded to push with OK", address.uri)
            Future.successful(Done)

          case _ =>
            val message =
              s"Endpoint [${address.uri}] responded to push with unexpected status: [${response.status.value}]"
            log.warning(message)
            Future.failed(EndpointFailure(message))
        }
      }

  private def pullCrate(
    address: HttpEndpointAddress,
    crate: Crate.Id,
    endpointCredentials: HttpCredentials
  ): Future[Option[Source[ByteString, NotUsed]]] =
    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = s"${address.uri}/crates/$crate"
        ).addCredentials(endpointCredentials)
      )
      .flatMap {
        case HttpResponse(status, _, entity, _) =>
          status match {
            case StatusCodes.OK =>
              log.info("Endpoint [{}] responded to pull with content", address.uri)
              Future.successful(Some(entity.dataBytes.mapMaterializedValue(_ => NotUsed)))

            case StatusCodes.NotFound =>
              val _ = entity.dataBytes.runWith(Sink.cancelled[ByteString])
              log.warning("Endpoint [{}] responded to pull with no content", address.uri)
              Future.successful(None)

            case _ =>
              val _ = entity.dataBytes.runWith(Sink.cancelled[ByteString])
              val message = s"Endpoint [${address.uri}] responded to pull with unexpected status: [${status.value}]"
              log.warning(message)
              Future.failed(EndpointFailure(message))
          }
      }

  private def discardCrate(
    address: HttpEndpointAddress,
    crate: Crate.Id,
    endpointCredentials: HttpCredentials
  ): Future[Boolean] =
    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.DELETE,
          uri = s"${address.uri}/crates/$crate"
        ).addCredentials(endpointCredentials)
      )
      .flatMap {
        case HttpResponse(status, _, entity, _) =>
          val _ = entity.dataBytes.runWith(Sink.cancelled[ByteString])
          status match {
            case StatusCodes.OK =>
              log.info("Endpoint [{}] responded to discard with OK", address.uri)
              Future.successful(true)

            case StatusCodes.InternalServerError =>
              log.error("Endpoint [{}] failed to discard crate [{}]", address.uri, crate)
              Future.successful(false)

            case _ =>
              val message = s"Endpoint [${address.uri}] responded to discard with unexpected status: [${status.value}]"
              log.warning(message)
              Future.failed(EndpointFailure(message))
          }
      }
}
