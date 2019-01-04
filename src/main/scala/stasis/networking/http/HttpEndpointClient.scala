package stasis.networking.http

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
import stasis.networking.exceptions.{CredentialsFailure, EndpointFailure, ReservationFailure}
import stasis.networking.{EndpointClient, EndpointCredentials}
import stasis.packaging.{Crate, Manifest}
import stasis.persistence.{CrateStorageRequest, CrateStorageReservation}
import scala.concurrent.{ExecutionContext, Future}

import stasis.packaging.Crate.Id

@SuppressWarnings(Array("org.wartremover.warts.Any"))
class HttpEndpointClient(
  override protected val credentials: EndpointCredentials[HttpEndpointAddress, HttpCredentials]
)(implicit val system: ActorSystem)
    extends EndpointClient[HttpEndpointAddress, HttpCredentials] {

  import HttpEndpoint._
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  private implicit val mat: ActorMaterializer = ActorMaterializer()
  private implicit val ec: ExecutionContext = system.dispatcher

  private val log = Logging(system, this.getClass.getName)

  override def push(
    address: HttpEndpointAddress,
    manifest: Manifest,
    content: Source[ByteString, NotUsed]
  ): Future[Done] = {
    log.debug("Pushing to endpoint [{}] content with manifest [{}]", address.uri, manifest)

    credentials.provide(address) match {
      case Some(endpointCredentials) =>
        for {
          reservation <- reserveStorage(address, manifest, endpointCredentials)
          result <- pushCrate(address, manifest, content, reservation, endpointCredentials)
        } yield {
          result
        }

      case None =>
        val message = s"Push to endpoint [${address.uri}] failed; unable to retrieve credentials"
        log.error(message)
        Future.failed(CredentialsFailure(message))
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

    credentials.provide(address) match {
      case Some(endpointCredentials) =>
        reserveStorage(address, manifest, endpointCredentials).map { reservation =>
          val _ = pushCrate(address, manifest, content, reservation, endpointCredentials)
          sink.mapMaterializedValue(_ => Future.successful(Done))
        }

      case None =>
        val message = s"Push to endpoint [${address.uri}] via sink failed; unable to retrieve credentials"
        log.error(message)
        Future.failed(CredentialsFailure(message))
    }
  }

  override def pull(
    address: HttpEndpointAddress,
    crate: Crate.Id
  ): Future[Option[Source[ByteString, NotUsed]]] = {
    log.debug("Pulling from endpoint [{}] crate with ID [{}]", address.uri, crate)

    credentials.provide(address) match {
      case Some(endpointCredentials) =>
        pullCrate(address, crate, endpointCredentials)

      case None =>
        val message = s"Pull from endpoint [${address.uri}] failed; unable to retrieve credentials"
        log.error(message)
        Future.failed(CredentialsFailure(message))
    }
  }

  override def discard(address: HttpEndpointAddress, crate: Id): Future[Boolean] = {
    log.debug("Discarding from endpoint [{}] crate with ID [{}]", address.uri, crate)

    credentials.provide(address) match {
      case Some(endpointCredentials) =>
        discardCrate(address, crate, endpointCredentials)

      case None =>
        val message = s"Discard from endpoint [${address.uri}] failed; unable to retrieve credentials"
        log.error(message)
        Future.failed(CredentialsFailure(message))
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
            uri = s"${address.uri}/reserve",
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
          uri = s"${address.uri}/crate/${manifest.crate}?reservation=${reservation.id}",
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
          uri = s"${address.uri}/crate/$crate"
        ).addCredentials(endpointCredentials)
      )
      .flatMap {
        case HttpResponse(status, _, entity, _) =>
          status match {
            case StatusCodes.OK =>
              log.info("Endpoint [{}] responded to pull with content", address.uri)
              Future.successful(Some(entity.dataBytes.mapMaterializedValue(_ => NotUsed)))

            case StatusCodes.NotFound =>
              val _ = entity.discardBytes()
              log.warning("Endpoint [{}] responded to pull with no content", address.uri)
              Future.successful(None)

            case _ =>
              val _ = entity.discardBytes()
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
          uri = s"${address.uri}/crate/$crate"
        ).addCredentials(endpointCredentials)
      )
      .flatMap {
        case HttpResponse(status, _, entity, _) =>
          val _ = entity.discardBytes()
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
