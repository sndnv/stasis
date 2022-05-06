package stasis.core.networking.grpc

import akka.NotUsed
import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.LoggerOps
import akka.grpc.scaladsl.{GrpcExceptionHandler, GrpcMarshalling}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model.Uri.Path.Segment
import akka.http.scaladsl.model.headers.HttpCredentials
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{Materializer, SystemMaterializer}
import org.slf4j.LoggerFactory
import stasis.core.networking.Endpoint
import stasis.core.networking.exceptions.{CredentialsFailure, EndpointFailure, ReservationFailure}
import stasis.core.packaging.Manifest
import stasis.core.persistence.reservations.ReservationStoreView
import stasis.core.routing.{Node, Router}
import stasis.core.security.NodeAuthenticator
import stasis.core.security.tls.EndpointContext
import stasis.core.security.tls.EndpointContext.RichServerBuilder

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class GrpcEndpoint(
  router: Router,
  reservationStore: ReservationStoreView,
  override protected val authenticator: NodeAuthenticator[HttpCredentials]
)(implicit system: ActorSystem)
    extends Endpoint[HttpCredentials] {

  import internal.Implicits._

  private implicit val mat: Materializer = SystemMaterializer(system).materializer
  private implicit val ec: ExecutionContext = system.dispatcher

  private val log = LoggerFactory.getLogger(this.getClass.getName)

  def start(interface: String, port: Int, context: Option[EndpointContext]): Future[Http.ServerBinding] =
    Http()
      .newServerAt(
        interface = interface,
        port = port
      )
      .withContext(context)
      .bind(authenticated(grpcHandler))

  def reserve(node: Node.Id, reserveRequest: proto.ReserveRequest): Future[proto.ReserveResponse] =
    internal.Requests.Reserve.unmarshal(reserveRequest) match {
      case Right(storageRequest) =>
        router
          .reserve(storageRequest)
          .map {
            case Some(reservation) =>
              log.debugN("Reservation created for node [{}]: [{}]", node, reservation)
              proto.ReserveResponse().withReservation(reservation.id)

            case None =>
              val message = s"Reservation rejected for node [${node.toString}]"
              log.warn(message)
              proto.ReserveResponse().withFailure(ReservationFailure(message))
          }
          .recover { case NonFatal(e) =>
            val message = s"Reservation failed for node [${node.toString}]: [${e.getClass.getSimpleName} - ${e.getMessage}]"
            log.error(message)
            proto.ReserveResponse().withFailure(ReservationFailure(message))
          }

      case Left(failure) =>
        val message =
          s"Node [${node.toString}] made reservation request with missing data: [${failure.getClass.getSimpleName} - ${failure.getMessage}]"
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
            .toRight(new IllegalArgumentException(s"Node [${node.toString}] made push request with missing reservation"))

        case _ =>
          Left(new IllegalArgumentException(s"Node [${node.toString}] made push request with empty stream"))
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
                    log.debug("Crate created with manifest: [{}]", manifest)
                    proto.PushResponse().withComplete(proto.Complete())
                  }
                  .recover { case NonFatal(e) =>
                    val message = s"Push failed for node [${node.toString}]: [${e.getClass.getSimpleName} - ${e.getMessage}]"
                    log.error(message)
                    proto.PushResponse().withFailure(EndpointFailure(message))
                  }

              case None =>
                val message = s"Node [${node.toString}] failed to push crate; reservation [${reservationId.toString}] not found"
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
              log.debugN("Node [{}] pulling crate [{}]", node, crateId)
              Future.successful(source.map(proto.PullChunk(request.crate, _)))

            case None =>
              log.warnN("Node [{}] failed to pull crate [{}]", node, crateId)
              Future.successful(Source.empty[proto.PullChunk])
          }

      case None =>
        val message = s"Node [${node.toString}] made pull request with missing crate: [${request.crate.toString}]"
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
            log.debugN("Node [{}] discarded crate [{}]", node, crateId)
            proto.DiscardResponse().withComplete(proto.Complete())
          }
          .recover { case NonFatal(e) =>
            val message = s"Discard failed for node [${node.toString}]: [${e.getMessage}]"
            log.error(message)
            proto.DiscardResponse().withFailure(EndpointFailure(message))
          }

      case None =>
        val message = s"Node [${node.toString}] made discard request with missing crate: [${request.crate.toString}]"
        log.error(message)
        Future.successful(proto.DiscardResponse().withFailure(new IllegalArgumentException(message)))
    }

  def grpcHandler(request: HttpRequest, node: Node.Id): Future[HttpResponse] = {
    import proto.StasisEndpoint.Serializers._

    request.uri.path match {
      case Path.Slash(Segment(proto.StasisEndpoint.name, Path.Slash(Segment(method, Path.Empty)))) =>
        GrpcMarshalling
          .negotiated(
            req = request,
            f = (reader, writer) => {
              val response = method match {
                case "Reserve" =>
                  GrpcMarshalling
                    .unmarshal(request.entity.dataBytes)(ReserveRequestSerializer, mat, reader)
                    .flatMap(reserve(node, _))
                    .map(e => GrpcMarshalling.marshal(e)(ReserveResponseSerializer, writer, system))

                case "Push" =>
                  GrpcMarshalling
                    .unmarshalStream(request.entity.dataBytes)(PushChunkSerializer, mat, reader)
                    .flatMap(push(node, _))
                    .map(e => GrpcMarshalling.marshal(e)(PushResponseSerializer, writer, system))

                case "Pull" =>
                  GrpcMarshalling
                    .unmarshal(request.entity.dataBytes)(PullRequestSerializer, mat, reader)
                    .flatMap(pull(node, _))
                    .map(e => GrpcMarshalling.marshalStream(e)(PullChunkSerializer, writer, system))

                case "Discard" =>
                  GrpcMarshalling
                    .unmarshal(request.entity.dataBytes)(DiscardRequestSerializer, mat, reader)
                    .flatMap(discard(node, _))
                    .map(e => GrpcMarshalling.marshal(e)(DiscardResponseSerializer, writer, system))

                case _ =>
                  Future.successful(HttpResponse(StatusCodes.MethodNotAllowed))
              }

              response.recoverWith(GrpcExceptionHandler.default(system, writer))
            }
          )
          .getOrElse(Future.successful(HttpResponse(StatusCodes.UnsupportedMediaType)))

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
      .recover { case CredentialsFailure(failure) =>
        log.warnN("Rejecting request for [{}]: [{}]", request.uri, failure)
        HttpResponse(StatusCodes.Unauthorized)
      }

  }
}
