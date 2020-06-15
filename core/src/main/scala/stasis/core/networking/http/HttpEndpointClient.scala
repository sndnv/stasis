package stasis.core.networking.http

import akka.actor.typed.scaladsl.LoggerOps
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.HttpCredentials
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.{Http, HttpsConnectionContext}
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{OverflowStrategy, QueueOfferResult}
import akka.util.ByteString
import akka.{Done, NotUsed}
import org.slf4j.LoggerFactory
import stasis.core.networking.EndpointClient
import stasis.core.networking.exceptions.{ClientFailure, CredentialsFailure, EndpointFailure, ReservationFailure}
import stasis.core.packaging.{Crate, Manifest}
import stasis.core.persistence.{CrateStorageRequest, CrateStorageReservation}
import stasis.core.security.NodeCredentialsProvider

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.NonFatal

@SuppressWarnings(Array("org.wartremover.warts.Any"))
class HttpEndpointClient(
  override protected val credentials: NodeCredentialsProvider[HttpEndpointAddress, HttpCredentials],
  context: Option[HttpsConnectionContext],
  requestBufferSize: Int
)(implicit system: ActorSystem[SpawnProtocol.Command])
    extends EndpointClient[HttpEndpointAddress, HttpCredentials] {

  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.core.api.Formats._

  private implicit val ec: ExecutionContext = system.executionContext

  private val log = LoggerFactory.getLogger(this.getClass.getName)

  private val http = Http()(system.classicSystem)

  private val clientContext: HttpsConnectionContext = context match {
    case Some(context) => context
    case None          => http.defaultClientHttpsContext
  }

  private val queue = Source
    .queue(
      bufferSize = requestBufferSize,
      overflowStrategy = OverflowStrategy.backpressure
    )
    .via(http.superPool[Promise[HttpResponse]](connectionContext = clientContext))
    .to(
      Sink.foreach {
        case (response, promise) => val _ = promise.complete(response)
      }
    )
    .run()

  private def offer(request: HttpRequest): Future[HttpResponse] = {
    val promise = Promise[HttpResponse]()

    queue
      .offer((request, promise))
      .flatMap(HttpEndpointClient.processOfferResult(request, promise))
  }

  override def push(
    address: HttpEndpointAddress,
    manifest: Manifest,
    content: Source[ByteString, NotUsed]
  ): Future[Done] = {
    log.debugN("Pushing to endpoint [{}] content with manifest [{}]", address.uri, manifest)

    credentials
      .provide(address)
      .recoverWith {
        case NonFatal(e) =>
          val message =
            s"Push to endpoint [${address.uri.toString()}] failed for crate [${manifest.crate.toString}];" +
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
    log.debugN("Building content sink for endpoint [{}] with manifest [{}]", address.uri, manifest)

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
            s"Push to endpoint [${address.uri.toString()}] via sink failed for crate [${manifest.crate.toString}];" +
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
    log.debugN("Pulling from endpoint [{}] crate with ID [{}]", address.uri, crate)

    credentials
      .provide(address)
      .recoverWith {
        case NonFatal(e) =>
          val message =
            s"Pull from endpoint [${address.uri.toString}] failed for crate [${crate.toString}];" +
              s" unable to retrieve credentials: [${e.getMessage}]"
          log.error(message)
          Future.failed(CredentialsFailure(message))
      }
      .flatMap { endpointCredentials =>
        pullCrate(address, crate, endpointCredentials)
      }
  }

  override def discard(address: HttpEndpointAddress, crate: Crate.Id): Future[Boolean] = {
    log.debugN("Discarding from endpoint [{}] crate with ID [{}]", address.uri, crate)

    credentials
      .provide(address)
      .recoverWith {
        case NonFatal(e) =>
          val message =
            s"Discard from endpoint [${address.uri.toString}] failed for crate [${crate.toString}];" +
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
      offer(
        request = HttpRequest(
          method = HttpMethods.PUT,
          uri = address.uri.withPath(address.uri.path ?/ "reservations"),
          entity = requestEntity
        ).addCredentials(endpointCredentials)
      ).flatMap {
        case HttpResponse(status, _, entity, _) =>
          status match {
            case StatusCodes.OK =>
              Unmarshal(entity).to[CrateStorageReservation].map { reservation =>
                log.debug(
                  "Endpoint [{}] responded to storage request [{}] with: [{}]",
                  address.uri,
                  storageRequest,
                  reservation
                )

                reservation
              }

            case StatusCodes.InsufficientStorage =>
              val message =
                s"Endpoint [${address.uri.toString}] was unable to reserve enough storage for request [${storageRequest.toString}]"
              log.warn(message)
              Future.failed(ReservationFailure(message))

            case _ =>
              val message =
                s"Endpoint [${address.uri.toString}] responded to storage request with unexpected status: [${status.value}]"
              log.warn(message)
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
    offer(
      request = HttpRequest(
        method = HttpMethods.PUT,
        uri = address.uri
          .withPath(address.uri.path ?/ "crates" / manifest.crate.toString)
          .withQuery(Uri.Query("reservation" -> reservation.id.toString)),
        entity = HttpEntity(ContentTypes.`application/octet-stream`, content)
      ).addCredentials(endpointCredentials)
    ).flatMap { response =>
      response.status match {
        case StatusCodes.OK =>
          log.debugN("Endpoint [{}] responded to push for crate [{}] with OK", address.uri, manifest.crate)
          Future.successful(Done)

        case _ =>
          val message =
            s"Endpoint [${address.uri.toString}] responded to push for crate [${manifest.crate.toString}] " +
              s"with unexpected status: [${response.status.value}]"
          log.warn(message)
          Future.failed(EndpointFailure(message))
      }
    }

  private def pullCrate(
    address: HttpEndpointAddress,
    crate: Crate.Id,
    endpointCredentials: HttpCredentials
  ): Future[Option[Source[ByteString, NotUsed]]] =
    offer(
      request = HttpRequest(
        method = HttpMethods.GET,
        uri = address.uri.withPath(address.uri.path ?/ "crates" / crate.toString)
      ).addCredentials(endpointCredentials)
    ).flatMap {
      case HttpResponse(status, _, entity, _) =>
        status match {
          case StatusCodes.OK =>
            log.debugN("Endpoint [{}] responded to pull with content for crate [{}]", address.uri, crate)
            Future.successful(Some(entity.dataBytes.mapMaterializedValue(_ => NotUsed)))

          case StatusCodes.NotFound =>
            val _ = entity.dataBytes.runWith(Sink.cancelled[ByteString])
            log.warnN("Endpoint [{}] responded to pull with no content for crate [{}]", address.uri, crate)
            Future.successful(None)

          case _ =>
            val _ = entity.dataBytes.runWith(Sink.cancelled[ByteString])
            val message =
              s"Endpoint [${address.uri.toString}] responded to pull for crate [${crate.toString}] " +
                s"with unexpected status: [${status.value}]"
            log.warn(message)
            Future.failed(EndpointFailure(message))
        }
    }

  private def discardCrate(
    address: HttpEndpointAddress,
    crate: Crate.Id,
    endpointCredentials: HttpCredentials
  ): Future[Boolean] =
    offer(
      request = HttpRequest(
        method = HttpMethods.DELETE,
        uri = address.uri.withPath(address.uri.path ?/ "crates" / crate.toString)
      ).addCredentials(endpointCredentials)
    ).flatMap {
      case HttpResponse(status, _, entity, _) =>
        val _ = entity.dataBytes.runWith(Sink.cancelled[ByteString])
        status match {
          case StatusCodes.OK =>
            log.debugN("Endpoint [{}] responded to discard for crate [{}] with OK", address.uri, crate)
            Future.successful(true)

          case StatusCodes.InternalServerError =>
            log.errorN("Endpoint [{}] failed to discard crate [{}]", address.uri, crate)
            Future.successful(false)

          case _ =>
            val message =
              s"Endpoint [${address.uri.toString}] responded to discard for crate [${crate.toString}] " +
                s"with unexpected status: [${status.value}]"
            log.warn(message)
            Future.failed(EndpointFailure(message))
        }
    }
}

object HttpEndpointClient {
  def apply(
    credentials: NodeCredentialsProvider[HttpEndpointAddress, HttpCredentials],
    context: Option[HttpsConnectionContext],
    requestBufferSize: Int
  )(implicit system: ActorSystem[SpawnProtocol.Command]): HttpEndpointClient =
    new HttpEndpointClient(
      credentials = credentials,
      context = context,
      requestBufferSize = requestBufferSize
    )

  def apply(
    credentials: NodeCredentialsProvider[HttpEndpointAddress, HttpCredentials],
    requestBufferSize: Int
  )(implicit system: ActorSystem[SpawnProtocol.Command]): HttpEndpointClient =
    HttpEndpointClient(
      credentials = credentials,
      context = None,
      requestBufferSize = requestBufferSize
    )

  def apply(
    credentials: NodeCredentialsProvider[HttpEndpointAddress, HttpCredentials],
    context: HttpsConnectionContext,
    requestBufferSize: Int
  )(implicit system: ActorSystem[SpawnProtocol.Command]): HttpEndpointClient =
    HttpEndpointClient(
      credentials = credentials,
      context = Some(context),
      requestBufferSize = requestBufferSize
    )

  def processOfferResult[T](request: HttpRequest, promise: Promise[T])(result: QueueOfferResult): Future[T] = {
    def clientFailure(cause: String): Future[T] =
      Future.failed(ClientFailure(s"[${request.method.value}] request for endpoint [${request.uri.toString}] failed; $cause"))

    result match {
      case QueueOfferResult.Enqueued    => promise.future
      case QueueOfferResult.Dropped     => clientFailure(cause = "dropped by stream")
      case QueueOfferResult.Failure(e)  => clientFailure(cause = s"${e.getClass.getSimpleName}: ${e.getMessage}")
      case QueueOfferResult.QueueClosed => clientFailure(cause = "stream closed")
    }
  }
}
