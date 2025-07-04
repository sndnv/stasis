package stasis.core.networking.http

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

import org.apache.pekko.Done
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.marshalling.Marshal
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.headers.HttpCredentials
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import org.apache.pekko.stream.scaladsl.Keep
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.core.api.PoolClient
import stasis.core.networking.EndpointClient
import stasis.core.networking.exceptions.CredentialsFailure
import stasis.core.networking.exceptions.EndpointFailure
import stasis.core.networking.exceptions.ReservationFailure
import stasis.core.packaging.Crate
import stasis.core.packaging.Manifest
import stasis.core.persistence.CrateStorageRequest
import stasis.core.persistence.CrateStorageReservation
import stasis.core.security.NodeCredentialsProvider
import io.github.sndnv.layers.security.tls.EndpointContext
import io.github.sndnv.layers.streaming.Operators.ExtendedByteStringSource
import io.github.sndnv.layers.streaming.Operators.ExtendedSource

@SuppressWarnings(Array("org.wartremover.warts.Any"))
class HttpEndpointClient(
  override protected val credentials: NodeCredentialsProvider[HttpEndpointAddress, HttpCredentials],
  override protected val context: Option[EndpointContext],
  override protected val config: PoolClient.Config,
  private val maxChunkSize: Int
)(implicit override protected val system: ActorSystem[Nothing])
    extends EndpointClient[HttpEndpointAddress, HttpCredentials]
    with PoolClient {

  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  import stasis.core.api.Formats._

  private implicit val ec: ExecutionContext = system.executionContext

  override protected val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  override def push(address: HttpEndpointAddress, manifest: Manifest, content: Source[ByteString, NotUsed]): Future[Done] = {
    log.debugN("Pushing content for endpoint [{}] with manifest [{}]", address.uri, manifest)

    for {
      endpointCredentials <- credentials
        .provide(address)
        .recoverWith { case NonFatal(e) =>
          val message =
            s"Push to endpoint [${address.uri.toString()}] failed for crate [${manifest.crate.toString}];" +
              s" unable to retrieve credentials: [${e.getMessage}]"
          log.error(message)
          Future.failed(CredentialsFailure(message))
        }
      reservation <- reserveStorage(address, manifest, endpointCredentials)
      result <- pushCrate(
        address = address,
        manifest = manifest,
        content = content,
        reservation = reservation,
        endpointCredentials = endpointCredentials,
        maxChunkSize = maxChunkSize
      )
    } yield {
      result
    }
  }

  override def push(address: HttpEndpointAddress, manifest: Manifest): Future[Sink[ByteString, Future[Done]]] = {
    log.debugN("Building content sink for endpoint [{}] with manifest [{}]", address.uri, manifest)

    credentials
      .provide(address)
      .recoverWith { case NonFatal(e) =>
        val message =
          s"Push to endpoint [${address.uri.toString()}] via sink failed for crate [${manifest.crate.toString}];" +
            s" unable to retrieve credentials: [${e.getMessage}]"
        log.error(message)
        Future.failed(CredentialsFailure(message))
      }
      .flatMap { endpointCredentials =>
        reserveStorage(address, manifest, endpointCredentials).map { reservation =>
          val (sink, content) = Source
            .asSubscriber[ByteString]
            .toMat(Sink.asPublisher[ByteString](fanout = true))(Keep.both)
            .mapMaterializedValue { case (subscriber, publisher) =>
              (Sink.fromSubscriber(subscriber), Source.fromPublisher(publisher))
            }
            .run()

          val result = pushCrate(
            address = address,
            manifest = manifest,
            content = content,
            reservation = reservation,
            endpointCredentials = endpointCredentials,
            maxChunkSize = maxChunkSize
          )

          sink.mapMaterializedValue(_ => result)
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
      .recoverWith { case NonFatal(e) =>
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
      .recoverWith { case NonFatal(e) =>
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
      ).flatMap { case HttpResponse(status, _, entity, _) =>
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
    endpointCredentials: HttpCredentials,
    maxChunkSize: Int
  ): Future[Done] =
    offer(
      request = HttpRequest(
        method = HttpMethods.PUT,
        uri = address.uri
          .withPath(address.uri.path ?/ "crates" / manifest.crate.toString)
          .withQuery(Uri.Query("reservation" -> reservation.id.toString)),
        entity = HttpEntity(
          ContentTypes.`application/octet-stream`,
          content.repartition(withMaximumElementSize = maxChunkSize)
        )
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
    ).flatMap { case HttpResponse(status, _, entity, _) =>
      status match {
        case StatusCodes.OK =>
          log.debugN("Endpoint [{}] responded to pull with content for crate [{}]", address.uri, crate)
          Future.successful(Some(entity.dataBytes.mapMaterializedValue(_ => NotUsed)))

        case StatusCodes.NotFound =>
          log.warnN("Endpoint [{}] responded to pull with no content for crate [{}]", address.uri, crate)
          entity.dataBytes.cancelled().map(_ => None)

        case _ =>
          val message =
            s"Endpoint [${address.uri.toString}] responded to pull for crate [${crate.toString}] " +
              s"with unexpected status: [${status.value}]"
          log.warn(message)
          entity.dataBytes.cancelled().flatMap(_ => Future.failed(EndpointFailure(message)))
      }
    }

  private def discardCrate(
    address: HttpEndpointAddress,
    crate: Crate.Id,
    endpointCredentials: HttpCredentials
  ): Future[Boolean] =
    for {
      HttpResponse(status, _, entity, _) <- offer(
        request = HttpRequest(
          method = HttpMethods.DELETE,
          uri = address.uri.withPath(address.uri.path ?/ "crates" / crate.toString)
        ).addCredentials(endpointCredentials)
      )
      _ <- entity.dataBytes.cancelled()
      result <- status match {
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
    } yield {
      result
    }
}

object HttpEndpointClient {
  def apply(
    credentials: NodeCredentialsProvider[HttpEndpointAddress, HttpCredentials],
    context: Option[EndpointContext],
    maxChunkSize: Int,
    config: PoolClient.Config
  )(implicit system: ActorSystem[Nothing]): HttpEndpointClient =
    new HttpEndpointClient(
      credentials = credentials,
      context = context,
      maxChunkSize = maxChunkSize,
      config = config
    )

  def apply(
    credentials: NodeCredentialsProvider[HttpEndpointAddress, HttpCredentials],
    maxChunkSize: Int,
    config: PoolClient.Config
  )(implicit system: ActorSystem[Nothing]): HttpEndpointClient =
    HttpEndpointClient(
      credentials = credentials,
      context = None,
      maxChunkSize = maxChunkSize,
      config = config
    )

  def apply(
    credentials: NodeCredentialsProvider[HttpEndpointAddress, HttpCredentials],
    context: EndpointContext,
    maxChunkSize: Int,
    config: PoolClient.Config
  )(implicit system: ActorSystem[Nothing]): HttpEndpointClient =
    HttpEndpointClient(
      credentials = credentials,
      context = Some(context),
      maxChunkSize = maxChunkSize,
      config = config
    )
}
