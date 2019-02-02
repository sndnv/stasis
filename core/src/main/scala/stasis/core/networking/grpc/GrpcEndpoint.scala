package stasis.core.networking.grpc

import java.util.UUID

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.Logging
import akka.grpc.Codecs
import akka.grpc.scaladsl.{GrpcExceptionHandler, GrpcMarshalling}
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model.Uri.Path.Segment
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.{Http, Http2, HttpConnectionContext}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import stasis.core.networking.Endpoint
import stasis.core.networking.exceptions.{CredentialsFailure, EndpointFailure, ReservationFailure}
import stasis.core.packaging.Manifest
import stasis.core.persistence.reservations.ReservationStoreView
import stasis.core.routing.{Node, Router}
import stasis.core.security.NodeAuthenticator

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class GrpcEndpoint(
  router: Router,
  reservationStore: ReservationStoreView,
  override protected val authenticator: NodeAuthenticator[GrpcCredentials]
)(implicit system: ActorSystem)
    extends Endpoint[GrpcCredentials] {

  import internal.Implicits._

  private implicit val ec: ExecutionContext = system.dispatcher
  private implicit val mat: ActorMaterializer = ActorMaterializer()

  private val log = Logging(system, this.getClass.getName)

  def start(hostname: String, port: Int, connectionContext: HttpConnectionContext): Future[Http.ServerBinding] =
    Http2().bindAndHandleAsync(
      authenticated(grpcHandler),
      hostname,
      port,
      connectionContext
    )

  def reserve(node: Node.Id, reserveRequest: proto.ReserveRequest): Future[proto.ReserveResponse] =
    internal.Requests.Reserve.unmarshal(reserveRequest) match {
      case Right(storageRequest) =>
        router
          .reserve(storageRequest)
          .map {
            case Some(reservation) =>
              log.info("Reservation created for node [{}]: [{}]", node, reservation)
              proto.ReserveResponse().withReservation(reservation.id)

            case None =>
              val message = s"Reservation rejected for node [$node]"
              log.warning(message)
              proto.ReserveResponse().withFailure(ReservationFailure(message))
          }
          .recover {
            case NonFatal(e) =>
              val message = s"Reservation failed for node [$node]: [${e.getMessage}]"
              log.error(message)
              proto.ReserveResponse().withFailure(ReservationFailure(message))
          }

      case Left(failure) =>
        val message = s"Node [$node] made reservation request with missing data: [${failure.getMessage}]"
        log.error(message)
        Future.successful(proto.ReserveResponse().withFailure(EndpointFailure(message)))
    }

  def push(node: Node.Id, in: Source[proto.PushChunk, NotUsed]): Future[proto.PushResponse] =
    in.prefixAndTail(n = 1)
      .map(stream => (stream._1.toList, stream._2))
      .map {
        case (head :: Nil, tail) =>
          head.reservation
            .map(reservation => (reservation: UUID, Source.single(head) ++ tail))
            .toRight(new IllegalArgumentException(s"Node [$node] made push request with missing reservation"))

        case _ =>
          Left(new IllegalArgumentException(s"Node [$node] made push request with empty stream"))
      }
      .runWith(Sink.head)
      .flatMap {
        case Right((reservationId, incoming)) =>
          reservationStore
            .get(reservation = reservationId)
            .flatMap {
              case Some(reservation) =>
                val manifest = Manifest(source = node, reservation = reservation)
                router
                  .push(manifest, incoming.map(chunk => chunk.content))
                  .map { _ =>
                    log.info("Crate created with manifest: [{}]", manifest)
                    proto.PushResponse().withComplete(proto.Complete())
                  }
                  .recover {
                    case NonFatal(e) =>
                      val message = s"Push failed for node [$node]: [${e.getMessage}]"
                      log.error(message)
                      proto.PushResponse().withFailure(EndpointFailure(message))
                  }

              case None =>
                val message = s"Node [$node] failed to push crate; reservation [$reservationId] not found"
                log.error(message)
                Future.successful(proto.PushResponse().withFailure(EndpointFailure(message)))
            }

        case Left(failure) =>
          log.error(failure.message)
          Future.successful(proto.PushResponse().withFailure(EndpointFailure(failure.getMessage)))
      }

  def pull(node: Node.Id, request: proto.PullRequest): Future[Source[proto.PullChunk, NotUsed]] =
    request.crate match {
      case Some(crate) =>
        val crateId: UUID = crate
        router
          .pull(crateId)
          .flatMap {
            case Some(source) =>
              log.info("Node [{}] pulling crate [{}]", node, crateId)
              Future.successful(source.map(proto.PullChunk(request.crate, _)))

            case None =>
              log.warning("Node [{}] failed to pull crate [{}]", node, crateId)
              Future.successful(Source.empty[proto.PullChunk])
          }

      case None =>
        val message = s"Node [$node] made pull request with missing crate: [$request]"
        log.error(message)
        Future.failed(new IllegalArgumentException(message))
    }

  def discard(node: Node.Id, request: proto.DiscardRequest): Future[proto.DiscardResponse] =
    request.crate match {
      case Some(crate) =>
        val crateId: UUID = crate

        router
          .discard(crateId)
          .map { _ =>
            log.info("Node [{}] discarded crate [{}]", node, crateId)
            proto.DiscardResponse().withComplete(proto.Complete())
          }
          .recover {
            case NonFatal(e) =>
              val message = s"Discard failed for node [$node]: [${e.getMessage}]"
              log.error(message)
              proto.DiscardResponse().withFailure(EndpointFailure(message))
          }

      case None =>
        val message = s"Node [$node] made discard request with missing crate: [$request]"
        log.error(message)
        Future.successful(proto.DiscardResponse().withFailure(new IllegalArgumentException(message)))
    }

  def grpcHandler(request: HttpRequest, node: Node.Id): Future[HttpResponse] = {
    import proto.StasisEndpoint.Serializers._

    request.uri.path match {
      case Path.Slash(Segment(proto.StasisEndpoint.name, Path.Slash(Segment(method, Path.Empty)))) =>
        val response = method match {
          case "Reserve" =>
            val responseCodec = Codecs.negotiate(request)
            GrpcMarshalling
              .unmarshal(request)(ReserveRequestSerializer, mat)
              .flatMap(reserve(node, _))
              .map(e => GrpcMarshalling.marshal(e)(ReserveResponseSerializer, mat, responseCodec, system))

          case "Push" =>
            val responseCodec = Codecs.negotiate(request)
            GrpcMarshalling
              .unmarshalStream(request)(PushChunkSerializer, mat)
              .flatMap(push(node, _))
              .map(e => GrpcMarshalling.marshal(e)(PushResponseSerializer, mat, responseCodec, system))

          case "Pull" =>
            val responseCodec = Codecs.negotiate(request)
            GrpcMarshalling
              .unmarshal(request)(PullRequestSerializer, mat)
              .flatMap(pull(node, _))
              .map(e => GrpcMarshalling.marshalStream(e)(PullChunkSerializer, mat, responseCodec, system))

          case "Discard" =>
            val responseCodec = Codecs.negotiate(request)
            GrpcMarshalling
              .unmarshal(request)(DiscardRequestSerializer, mat)
              .flatMap(discard(node, _))
              .map(e => GrpcMarshalling.marshal(e)(DiscardResponseSerializer, mat, responseCodec, system))

          case _ =>
            Future.successful(HttpResponse(StatusCodes.MethodNotAllowed))
        }

        response.recoverWith(GrpcExceptionHandler.default)

      case _ =>
        Future.successful(HttpResponse(StatusCodes.NotFound))
    }
  }

  def authenticated(
    handler: (HttpRequest, Node.Id) => Future[HttpResponse]
  ): HttpRequest => Future[HttpResponse] = { request: HttpRequest =>
    val response = for {
      credentials <- internal.Credentials.extract(request)
      node <- authenticator.authenticate(credentials)
      response <- handler(request, node)
    } yield {
      response
    }

    response
      .recover {
        case CredentialsFailure(failure) =>
          log.warning(
            "Rejecting request for [{}]: [{}]",
            request.uri,
            failure
          )

          HttpResponse(StatusCodes.Unauthorized)
      }

  }
}
