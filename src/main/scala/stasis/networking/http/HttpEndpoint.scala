package stasis.networking.http

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.HttpCredentials
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.ActorMaterializer
import stasis.networking.Endpoint
import stasis.packaging.{Crate, Manifest}
import stasis.persistence.reservations.ReservationStoreView
import stasis.persistence.{CrateStorageRequest, CrateStorageReservation}
import stasis.routing.Router
import stasis.security.NodeAuthenticator

import scala.concurrent.{ExecutionContextExecutor, Future}

class HttpEndpoint(
  router: Router,
  reservationStore: ReservationStoreView,
  override protected val authenticator: NodeAuthenticator[HttpCredentials]
)(implicit val system: ActorSystem)
    extends Endpoint[HttpCredentials] {

  import HttpEndpoint._
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  private implicit val mat: ActorMaterializer = ActorMaterializer()
  private implicit val ec: ExecutionContextExecutor = system.dispatcher

  private val log = Logging(system, this.getClass.getName)

  def start(hostname: String, port: Int): Future[Http.ServerBinding] =
    Http().bindAndHandle(routes, hostname, port)

  val routes: Route =
    (extractMethod & extractUri & extractClientIP & extractRequest) { (method, uri, remoteAddress, request) =>
      extractCredentials {
        case Some(credentials) =>
          authenticator.authenticate(credentials) match {
            case Some(node) =>
              concat(
                path("reserve") {
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
                path("crate" / JavaUUID) { crateId: Crate.Id =>
                  concat(
                    put {
                      parameters("reservation".as[java.util.UUID]) {
                        reservationId =>
                          onSuccess(reservationStore.get(reservationId)) {
                            case Some(reservation) if reservation.crate == crateId =>
                              extractDataBytes { stream =>
                                onSuccess(
                                  router.push(
                                    Manifest(
                                      source = node,
                                      reservation = reservation
                                    ),
                                    stream.mapMaterializedValue(_ => NotUsed)
                                  )
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

            case None =>
              val _ = request.discardEntityBytes()

              log.warning(
                "Rejecting [{}] request for [{}] with invalid credentials from [{}]",
                method.value,
                uri,
                remoteAddress
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

object HttpEndpoint {
  import java.util.concurrent.TimeUnit

  import play.api.libs.json._

  import scala.concurrent.duration.FiniteDuration

  implicit val stringToUuid: Unmarshaller[String, java.util.UUID] = Unmarshaller { implicit ec => param =>
    Future {
      java.util.UUID.fromString(param)
    }
  }

  implicit val finiteDurationFormat: Format[FiniteDuration] = Format(
    Reads[FiniteDuration] { js =>
      js.validate[Long].map(seconds => FiniteDuration(seconds, TimeUnit.SECONDS))
    },
    Writes[FiniteDuration] { duration =>
      JsNumber(duration.toSeconds)
    }
  )

  implicit val crateStorageRequestFormat: Format[CrateStorageRequest] = Json.format[CrateStorageRequest]
  implicit val crateStorageReservationFormat: Format[CrateStorageReservation] = Json.format[CrateStorageReservation]
}
