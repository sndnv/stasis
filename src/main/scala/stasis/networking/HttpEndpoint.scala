package stasis.networking

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import stasis.packaging.{Crate, Manifest}
import stasis.routing.Router
import stasis.security.NodeAuthenticator

trait HttpEndpoint extends Endpoint {
  import HttpEndpoint._
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  protected implicit val system: ActorSystem
  protected val router: Router
  protected val authenticator: NodeAuthenticator

  protected val manifestConfig: Manifest.Config

  private implicit lazy val mat: ActorMaterializer = ActorMaterializer()
  private implicit lazy val ec: ExecutionContextExecutor = system.dispatcher

  private lazy val log = Logging(system, this.getClass.getName)

  val routes: Route =
    (extractMethod & extractUri & extractClientIP) { (method, uri, remoteAddress) =>
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
                              complete {
                                router
                                  .push(manifest, stream.mapMaterializedValue(_ => NotUsed))
                                  .map { _ =>
                                    log.info("Crate created with manifest: [{}]", manifest)
                                    CrateCreated(manifest)
                                  }
                              }
                            }

                          case errors =>
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
              log.warning(
                "Rejecting [{}] request for [{}] with invalid credentials from [{}]",
                method.value,
                uri,
                remoteAddress
              )

              complete(StatusCodes.Unauthorized)

          }

        case None =>
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
  import play.api.libs.json.{Format, Json}

  case class CrateCreated(
    crateId: Crate.Id,
    copies: Int,
    retention: Long
  )

  object CrateCreated {
    def apply(manifest: Manifest): CrateCreated =
      CrateCreated(manifest.crate, manifest.copies, manifest.retention.toSeconds)
  }

  implicit val crateCreatedFormat: Format[CrateCreated] = Json.format[CrateCreated]

  implicit val manifestErrorSeqFormat: Format[Manifest.FieldError] = Json.format[Manifest.FieldError]
}
