package stasis.core.networking.http

import akka.NotUsed
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.event.Logging
import akka.http.scaladsl.model.headers.HttpCredentials
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.http.scaladsl.{ConnectionContext, Http}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.util.ByteString
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
)(implicit system: ActorSystem[SpawnProtocol])
    extends Endpoint[HttpCredentials] {

  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.core.api.Formats._

  private implicit val untypedSystem: akka.actor.ActorSystem = system.toUntyped
  private implicit val mat: ActorMaterializer = ActorMaterializer()
  private implicit val ec: ExecutionContextExecutor = system.executionContext

  private val log = Logging(untypedSystem, this.getClass.getName)

  def start(interface: String, port: Int, context: ConnectionContext): Future[Http.ServerBinding] =
    Http().bindAndHandle(
      handler = routes,
      interface = interface,
      port = port,
      connectionContext = context
    )

  private implicit def sanitizingExceptionHandler: ExceptionHandler =
    ExceptionHandler {
      case NonFatal(e) =>
        extractRequestEntity { entity =>
          val _ = entity.dataBytes.runWith(Sink.cancelled[ByteString])
          val failureReference = java.util.UUID.randomUUID()

          log.error(
            e,
            "Unhandled exception encountered: [{}]; failure reference is [{}]",
            e.getMessage,
            failureReference
          )

          complete(
            HttpResponse(
              status = StatusCodes.InternalServerError,
              entity = s"Failed to process request; failure reference is [$failureReference]"
            )
          )
        }
    }

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
                          log.info("Reservation created for node [{}]: [{}]", node, reservation)
                          complete(reservation)

                        case None =>
                          log.warning("Reservation rejected for node [{}]", node)
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
                                  log.info("Crate created with manifest: [{}]", manifest)
                                  complete(StatusCodes.OK)
                                }
                              }

                            case Some(reservation) =>
                              val _ = request.discardEntityBytes()

                              log.error(
                                "Node [{}] failed to push crate with ID [{}]; reservation [{}] is for crate [{}]",
                                node,
                                crateId,
                                reservationId,
                                reservation.crate
                              )

                              complete(StatusCodes.BadRequest)

                            case None =>
                              val _ = request.discardEntityBytes()

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
                          log.info("Node [{}] pulling crate [{}]", node, crateId)
                          complete(HttpEntity(ContentTypes.`application/octet-stream`, stream))

                        case None =>
                          log.warning("Node [{}] failed to pull crate [{}]", node, crateId)
                          complete(StatusCodes.NotFound)
                      }
                    },
                    delete {
                      onSuccess(router.discard(crateId)) { _ =>
                        log.info("Node [{}] discarded crate [{}]", node, crateId)
                        complete(StatusCodes.OK)
                      }
                    }
                  )
                }
              )

            case Failure(e) =>
              val _ = request.discardEntityBytes()

              log.warning(
                "Rejecting [{}] request for [{}] with invalid credentials from [{}]: [{}]",
                method.value,
                uri,
                remoteAddress,
                e
              )

              complete(StatusCodes.Unauthorized)

          }

        case None =>
          val _ = request.discardEntityBytes()

          log.warning(
            "Rejecting [{}] request for [{}] with no credentials from [{}]",
            method.value,
            uri,
            remoteAddress
          )

          complete(StatusCodes.Unauthorized)
      }
    }
}
