package stasis.core.networking.grpc

import java.util.UUID

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.grpc.scaladsl.GrpcExceptionHandler
import org.apache.pekko.grpc.scaladsl.GrpcMarshalling
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.HttpRequest
import org.apache.pekko.http.scaladsl.model.HttpResponse
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.Uri.Path
import org.apache.pekko.http.scaladsl.model.Uri.Path.Segment
import org.apache.pekko.http.scaladsl.model.headers.HttpCredentials
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.SystemMaterializer
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.slf4j.LoggerFactory
import stasis.core.networking.Endpoint
import stasis.core.networking.exceptions.CredentialsFailure
import stasis.core.networking.exceptions.EndpointFailure
import stasis.core.networking.exceptions.ReservationFailure
import stasis.core.packaging.Manifest
import stasis.core.persistence.reservations.ReservationStore
import stasis.core.routing.Node
import stasis.core.routing.Router
import stasis.core.security.NodeAuthenticator
import io.github.sndnv.layers.api.Metrics
import io.github.sndnv.layers.security.tls.EndpointContext
import io.github.sndnv.layers.security.tls.EndpointContext.RichServerBuilder
import io.github.sndnv.layers.telemetry.TelemetryContext

class GrpcEndpoint(
  router: Router,
  reservationStore: ReservationStore.View,
  override protected val authenticator: NodeAuthenticator[HttpCredentials]
)(implicit system: ActorSystem[Nothing], telemetry: TelemetryContext)
    extends Endpoint[HttpCredentials] {

  import internal.Implicits._

  private implicit val mat: Materializer = SystemMaterializer(system).materializer
  private implicit val ec: ExecutionContext = system.executionContext

  private val log = LoggerFactory.getLogger(this.getClass.getName)
  private val metrics = telemetry.metrics[Metrics.Endpoint]

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
              proto.ReserveResponse(proto.ReserveResponse.Result.Reservation(reservation.id))

            case None =>
              val message = s"Reservation rejected for node [${node.toString}]"
              log.warn(message)
              proto.ReserveResponse(proto.ReserveResponse.Result.Failure(ReservationFailure(message)))
          }
          .recover { case NonFatal(e) =>
            val message = s"Reservation failed for node [${node.toString}]: [${e.getClass.getSimpleName} - ${e.getMessage}]"
            log.error(message)
            proto.ReserveResponse(proto.ReserveResponse.Result.Failure(ReservationFailure(message)))
          }

      case Left(failure) =>
        val message =
          s"Node [${node.toString}] made reservation request with missing data: [${failure.getClass.getSimpleName} - ${failure.getMessage}]"
        log.error(message)
        Future.successful(proto.ReserveResponse(proto.ReserveResponse.Result.Failure(EndpointFailure(message))))
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
                    proto.PushResponse(proto.PushResponse.Result.Complete(proto.Complete()))
                  }
                  .recover { case NonFatal(e) =>
                    val message = s"Push failed for node [${node.toString}]: [${e.getClass.getSimpleName} - ${e.getMessage}]"
                    log.error(message)
                    proto.PushResponse(proto.PushResponse.Result.Failure(EndpointFailure(message)))
                  }

              case None =>
                val message = s"Node [${node.toString}] failed to push crate; reservation [${reservationId.toString}] not found"
                log.error(message)
                Future.successful(proto.PushResponse(proto.PushResponse.Result.Failure(EndpointFailure(message))))
            }

        case Left(failure) =>
          log.error(failure.message)
          Future.successful(proto.PushResponse(proto.PushResponse.Result.Failure(EndpointFailure(failure.getMessage))))
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
            proto.DiscardResponse(proto.DiscardResponse.Result.Complete(proto.Complete()))
          }
          .recover { case NonFatal(e) =>
            val message = s"Discard failed for node [${node.toString}]: [${e.getMessage}]"
            log.error(message)
            proto.DiscardResponse(proto.DiscardResponse.Result.Failure(EndpointFailure(message)))
          }

      case None =>
        val message = s"Node [${node.toString}] made discard request with missing crate: [${request.crate.toString}]"
        log.error(message)
        Future.successful(proto.DiscardResponse(proto.DiscardResponse.Result.Failure(new IllegalArgumentException(message))))
    }

  def grpcHandler(request: HttpRequest, node: Node.Id): Future[HttpResponse] = {
    import proto.StasisEndpoint.Serializers._

    val requestStart = System.currentTimeMillis()
    metrics.recordRequest(request)

    val response = request.uri.path match {
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

    response
      .map { actualResponse =>
        metrics.recordResponse(requestStart, request, actualResponse)
        actualResponse
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
