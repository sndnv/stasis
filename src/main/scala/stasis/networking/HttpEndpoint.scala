package stasis.networking

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.HttpCredentials
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import stasis.packaging.{Crate, Manifest}
import stasis.routing.Router
import stasis.security.NodeAuthenticator

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}

class HttpEndpoint(
  router: Router,
  manifestConfig: Manifest.Config,
  override protected val authenticator: NodeAuthenticator[HttpCredentials]
)(implicit val system: ActorSystem)
    extends Endpoint[HttpCredentials] {

  import Endpoint._
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  private implicit lazy val mat: ActorMaterializer = ActorMaterializer()
  private implicit lazy val ec: ExecutionContextExecutor = system.dispatcher

  private lazy val log = Logging(system, this.getClass.getName)

  def start(hostname: String, port: Int): Future[Http.ServerBinding] =
    Http().bindAndHandle(routes, hostname, port)

  val routes: Route =
    (extractMethod & extractUri & extractClientIP & extractRequest) { (method, uri, remoteAddress, request) =>
      extractCredentials {
        case Some(credentials) =>
          authenticator.authenticate(credentials) match {
            case Some(node) =>
              path("crate" / JavaUUID) { crateId: Crate.Id =>
                concat(
                  put {
                    parameters(
                      "copies".as[Int] ? manifestConfig.defaultCopies,
                      "retention".as[Long] ? manifestConfig.defaultRetention.toSeconds
                    ) {
                      (copies, retention) =>
                        val manifest = Manifest(
                          crate = crateId,
                          copies,
                          retention.seconds,
                          node
                        )

                        manifestConfig.getManifestErrors(manifest) match {
                          case Nil =>
                            extractDataBytes { stream =>
                              onSuccess(router.push(manifest, stream.mapMaterializedValue(_ => NotUsed))) { _ =>
                                complete {
                                  log.info("Crate created with manifest: [{}]", manifest)
                                  CrateCreated(manifest)
                                }
                              }
                            }

                          case errors =>
                            val _ = request.discardEntityBytes()
                            log.warning(
                              "Rejecting [{}] request for [{}] from [{}] with invalid parameters: [{}]",
                              method.value,
                              uri,
                              remoteAddress,
                              errors
                            )

                            complete(StatusCodes.BadRequest, errors)
                        }
                    }
                  },
                  get {
                    onSuccess(router.pull(crateId)) {
                      case Some(stream) =>
                        log.info("Node [{}] pulling crate with ID: [{}]", node, crateId)
                        complete(HttpEntity(ContentTypes.`application/octet-stream`, stream))

                      case None =>
                        log.warning("Node [{}] failed to pull crate with ID: [{}]", node, crateId)
                        complete(StatusCodes.NotFound)
                    }
                  }
                )
              }

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
