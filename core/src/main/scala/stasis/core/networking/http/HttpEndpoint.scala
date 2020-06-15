package stasis.core.networking.http

import akka.NotUsed
import akka.actor.typed.scaladsl.LoggerOps
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.http.scaladsl.model.headers.HttpCredentials
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.{ConnectionContext, Http}
import akka.stream.scaladsl.Sink
import akka.stream.{Materializer, SystemMaterializer}
import akka.util.ByteString
import org.slf4j.LoggerFactory
import stasis.core.networking.Endpoint
import stasis.core.packaging.{Crate, Manifest}
import stasis.core.persistence.CrateStorageRequest
import stasis.core.persistence.reservations.ReservationStoreView
import stasis.core.routing.Router
import stasis.core.security.NodeAuthenticator

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

@SuppressWarnings(Array("org.wartremover.warts.Any"))
class HttpEndpoint(
  router: Router,
  reservationStore: ReservationStoreView,
  override protected val authenticator: NodeAuthenticator[HttpCredentials]
)(implicit system: ActorSystem[SpawnProtocol.Command])
    extends Endpoint[HttpCredentials] {

  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.core.api.Formats._

  private implicit val ec: ExecutionContextExecutor = system.executionContext

  private val log = LoggerFactory.getLogger(this.getClass.getName)

  def start(interface: String, port: Int, context: ConnectionContext): Future[Http.ServerBinding] = {
    implicit val mat: Materializer = SystemMaterializer(system).materializer
    implicit val untyped: akka.actor.ActorSystem = system.classicSystem

    Http().bindAndHandle(
      handler = routes,
      interface = interface,
      port = port,
      connectionContext = context
    )
  }

  private implicit def sanitizingExceptionHandler: ExceptionHandler =
    ExceptionHandler {
      case NonFatal(e) =>
        extractRequestEntity { entity =>
          val _ = entity.dataBytes.runWith(Sink.cancelled[ByteString])
          val failureReference = java.util.UUID.randomUUID()

          log.error(
            "Unhandled exception encountered: [{}]; failure reference is [{}]",
            e.getMessage,
            failureReference,
            e
          )

          complete(
            StatusCodes.InternalServerError,
            HttpEntity(
              ContentTypes.`text/plain(UTF-8)`,
              s"Failed to process request; failure reference is [${failureReference.toString}]"
            )
          )
        }
    }

  private implicit def rejectionHandler: RejectionHandler =
    RejectionHandler
      .newBuilder()
      .handle {
        case MissingQueryParamRejection(parameterName) =>
          extractRequestEntity { entity =>
            val _ = entity.dataBytes.runWith(Sink.cancelled[ByteString])

            val message = s"Parameter [$parameterName] is missing, invalid or malformed"
            log.warn(message)

            complete(
              StatusCodes.BadRequest,
              HttpEntity(ContentTypes.`text/plain(UTF-8)`, message)
            )
          }
      }
      .handle {
        case ValidationRejection(_, _) =>
          extractRequestEntity { entity =>
            val _ = entity.dataBytes.runWith(Sink.cancelled[ByteString])

            val message = "Provided data is invalid or malformed"
            log.warn(message)

            complete(
              StatusCodes.BadRequest,
              HttpEntity(ContentTypes.`text/plain(UTF-8)`, message)
            )
          }
      }
      .result()
      .seal

  val routes: Route =
    (extractMethod & extractUri & extractClientIP & extractRequest) { (method, uri, remoteAddress, request) =>
      extractCredentials {
        case Some(credentials) =>
          onComplete(authenticator.authenticate(credentials)) {
            case Success(node) =>
              concat(
                path("reservations") {
                  put {
                    entity(as[CrateStorageRequest]) { storageRequest =>
                      onSuccess(router.reserve(storageRequest)) {
                        case Some(reservation) =>
                          log.debugN("Reservation created for node [{}]: [{}]", node, reservation)
                          complete(reservation)

                        case None =>
                          log.warn("Reservation rejected for node [{}]", node)
                          complete(StatusCodes.InsufficientStorage)
                      }
                    }
                  }
                },
                path("crates" / JavaUUID) { crateId: Crate.Id =>
                  concat(
                    put {
                      parameters("reservation".as[java.util.UUID]) {
                        reservationId =>
                          onSuccess(reservationStore.get(reservationId)) {
                            case Some(reservation) if reservation.crate == crateId =>
                              extractDataBytes { stream =>
                                val manifest = Manifest(source = node, reservation = reservation)
                                onSuccess(
                                  router.push(manifest, stream.mapMaterializedValue(_ => NotUsed))
                                ) { _ =>
                                  log.debug("Crate created with manifest: [{}]", manifest)
                                  complete(StatusCodes.OK)
                                }
                              }

                            case Some(reservation) =>
                              val _ = request.entity.dataBytes.runWith(Sink.cancelled[ByteString])

                              log.error(
                                "Node [{}] failed to push crate with ID [{}]; reservation [{}] is for crate [{}]",
                                node,
                                crateId,
                                reservationId,
                                reservation.crate
                              )

                              complete(StatusCodes.BadRequest)

                            case None =>
                              val _ = request.entity.dataBytes.runWith(Sink.cancelled[ByteString])

                              log.error(
                                "Node [{}] failed to push crate with ID [{}]; reservation [{}] not found",
                                node,
                                crateId,
                                reservationId
                              )

                              complete(StatusCodes.FailedDependency)
                          }
                      }
                    },
                    get {
                      onSuccess(router.pull(crateId)) {
                        case Some(stream) =>
                          log.debugN("Node [{}] pulling crate [{}]", node, crateId)
                          complete(HttpEntity(ContentTypes.`application/octet-stream`, stream))

                        case None =>
                          log.warnN("Node [{}] failed to pull crate [{}]", node, crateId)
                          complete(StatusCodes.NotFound)
                      }
                    },
                    delete {
                      onSuccess(router.discard(crateId)) { _ =>
                        log.debugN("Node [{}] discarded crate [{}]", node, crateId)
                        complete(StatusCodes.OK)
                      }
                    }
                  )
                }
              )

            case Failure(e) =>
              val _ = request.entity.dataBytes.runWith(Sink.cancelled[ByteString])

              log.warn(
                "Rejecting [{}] request for [{}] with invalid credentials from [{}]: [{}]",
                method.value,
                uri,
                remoteAddress,
                e
              )

              complete(StatusCodes.Unauthorized)

          }

        case None =>
          val _ = request.entity.dataBytes.runWith(Sink.cancelled[ByteString])

          log.warn(
            "Rejecting [{}] request for [{}] with no credentials from [{}]",
            method.value,
            uri,
            remoteAddress
          )

          complete(StatusCodes.Unauthorized)
      }
    }
}

object HttpEndpoint {
  def apply(
    router: Router,
    reservationStore: ReservationStoreView,
    authenticator: NodeAuthenticator[HttpCredentials]
  )(implicit system: ActorSystem[SpawnProtocol.Command]): HttpEndpoint =
    new HttpEndpoint(
      router = router,
      reservationStore = reservationStore,
      authenticator = authenticator
    )
}
