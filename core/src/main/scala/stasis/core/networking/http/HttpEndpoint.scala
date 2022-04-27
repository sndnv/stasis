package stasis.core.networking.http

import akka.NotUsed
import akka.actor.typed.scaladsl.LoggerOps
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.HttpCredentials
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import org.slf4j.LoggerFactory
import stasis.core.api.directives.EntityDiscardingDirectives
import stasis.core.networking.Endpoint
import stasis.core.packaging.{Crate, Manifest}
import stasis.core.persistence.CrateStorageRequest
import stasis.core.persistence.reservations.ReservationStoreView
import stasis.core.routing.Router
import stasis.core.security.NodeAuthenticator
import stasis.core.security.tls.EndpointContext

import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

@SuppressWarnings(Array("org.wartremover.warts.Any"))
class HttpEndpoint(
  router: Router,
  reservationStore: ReservationStoreView,
  override protected val authenticator: NodeAuthenticator[HttpCredentials]
)(implicit system: ActorSystem[SpawnProtocol.Command])
    extends Endpoint[HttpCredentials]
    with EntityDiscardingDirectives {

  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.core.api.Formats._

  private val log = LoggerFactory.getLogger(this.getClass.getName)

  def start(interface: String, port: Int, context: Option[EndpointContext]): Future[Http.ServerBinding] = {
    import EndpointContext._

    Http()
      .newServerAt(interface = interface, port = port)
      .withContext(context = context)
      .bindFlow(
        handlerFlow = (handleExceptions(sanitizingExceptionHandler) & handleRejections(rejectionHandler)) {
          routes
        }
      )
  }

  private val sanitizingExceptionHandler: ExceptionHandler =
    ExceptionHandler { case NonFatal(e) =>
      extractRequest { request =>
        val failureReference = java.util.UUID.randomUUID()

        log.errorN(
          "Unhandled exception encountered during [{}] request for [{}]: [{}]; failure reference is [{}]",
          request.method.value,
          request.uri.path.toString,
          e.getMessage,
          failureReference,
          e
        )

        discardEntity & complete(
          StatusCodes.InternalServerError,
          HttpEntity(
            ContentTypes.`text/plain(UTF-8)`,
            s"Failed to process request; failure reference is [${failureReference.toString}]"
          )
        )
      }
    }

  private val rejectionHandler: RejectionHandler =
    RejectionHandler
      .newBuilder()
      .handle { case MissingQueryParamRejection(parameterName) =>
        extractRequest { request =>
          val message = s"Parameter [$parameterName] is missing, invalid or malformed"

          log.warnN(
            "[{}] request for [{}] rejected: [{}]",
            request.method.value,
            request.uri.path.toString,
            message
          )

          discardEntity & complete(
            StatusCodes.BadRequest,
            HttpEntity(ContentTypes.`text/plain(UTF-8)`, message)
          )
        }
      }
      .handle { case ValidationRejection(rejectionMessage, _) =>
        extractRequest { request =>
          val message = s"Provided data is invalid or malformed: [$rejectionMessage]"

          log.warnN(
            "[{}] request for [{}] rejected: [{}]",
            request.method.value,
            request.uri.path.toString,
            message
          )

          discardEntity & complete(
            StatusCodes.BadRequest,
            HttpEntity(ContentTypes.`text/plain(UTF-8)`, message)
          )
        }
      }
      .result()
      .seal

  val routes: Route =
    (extractMethod & extractUri & extractClientIP) { (method, uri, remoteAddress) =>
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
                      parameters("reservation".as[java.util.UUID]) { reservationId =>
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
                            log.error(
                              "Node [{}] failed to push crate with ID [{}]; reservation [{}] is for crate [{}]",
                              node,
                              crateId,
                              reservationId,
                              reservation.crate
                            )

                            discardEntity & complete(StatusCodes.BadRequest)

                          case None =>
                            log.error(
                              "Node [{}] failed to push crate with ID [{}]; reservation [{}] not found",
                              node,
                              crateId,
                              reservationId
                            )

                            discardEntity & complete(StatusCodes.FailedDependency)
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
              log.warn(
                "Rejecting [{}] request for [{}] with invalid credentials from [{}]: [{} - {}]",
                method.value,
                uri,
                remoteAddress,
                e.getClass.getSimpleName,
                e.getMessage
              )

              discardEntity & complete(StatusCodes.Unauthorized)
          }

        case None =>
          log.warn(
            "Rejecting [{}] request for [{}] with no credentials from [{}]",
            method.value,
            uri,
            remoteAddress
          )

          discardEntity & complete(StatusCodes.Unauthorized)
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
