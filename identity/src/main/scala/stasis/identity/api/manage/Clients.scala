package stasis.identity.api.manage

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.Materializer
import stasis.core.api.directives.EntityDiscardingDirectives
import stasis.identity.api.Formats._
import stasis.identity.api.manage.requests.{CreateClient, UpdateClient, UpdateClientCredentials}
import stasis.identity.api.manage.responses.CreatedClient
import stasis.identity.model.clients.ClientStore
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.secrets.Secret

import scala.concurrent.ExecutionContext

class Clients(
  store: ClientStore,
  clientSecretConfig: Secret.ClientConfig
)(implicit system: ActorSystem, override val mat: Materializer)
    extends EntityDiscardingDirectives {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  private implicit val ec: ExecutionContext = system.dispatcher
  private val log: LoggingAdapter = Logging(system, this.getClass.getName)

  private implicit val secretConfig: Secret.ClientConfig = clientSecretConfig

  def routes(user: ResourceOwner.Id): Route =
    concat(
      pathEndOrSingleSlash {
        concat(
          get {
            onSuccess(store.clients) { clients =>
              log.debug("User [{}] successfully retrieved [{}] clients", user, clients.size)
              discardEntity & complete(clients.values)
            }
          },
          post {
            entity(as[CreateClient]) { request =>
              val client = request.toClient
              onSuccess(store.put(client)) { _ =>
                log.debug("User [{}] successfully created client [{}]", user, client.id)
                complete(CreatedClient(client.id))
              }
            }
          }
        )
      },
      pathPrefix(JavaUUID) { clientId =>
        onSuccess(store.get(clientId)) {
          case Some(client) =>
            concat(
              path("credentials") {
                put {
                  entity(as[UpdateClientCredentials]) { request =>
                    val (secret, salt) = request.toSecret()

                    onSuccess(store.put(client.copy(secret = secret, salt = salt))) { _ =>
                      log.debug("User [{}] successfully updated credentials for client [{}]", user, clientId)
                      complete(StatusCodes.OK)
                    }
                  }
                }
              },
              pathEndOrSingleSlash {
                concat(
                  get {
                    log.debug("User [{}] successfully retrieved client [{}]", user, clientId)
                    discardEntity & complete(client)
                  },
                  put {
                    entity(as[UpdateClient]) { request =>
                      onSuccess(
                        store.put(
                          client.copy(
                            tokenExpiration = request.tokenExpiration,
                            active = request.active
                          )
                        )
                      ) { _ =>
                        log.debug("User [{}] successfully updated client [{}]", user, clientId)
                        complete(StatusCodes.OK)
                      }
                    }
                  },
                  delete {
                    onSuccess(store.delete(clientId)) { _ =>
                      log.debug("User [{}] successfully deleted client [{}]", user, clientId)
                      discardEntity & complete(StatusCodes.OK)
                    }
                  }
                )
              }
            )

          case None =>
            log.warning("User [{}] made request for client [{}] but it was not found", user, clientId)
            discardEntity & complete(StatusCodes.NotFound)
        }
      }
    )
}

object Clients {
  def apply(
    store: ClientStore,
    clientSecretConfig: Secret.ClientConfig
  )(implicit system: ActorSystem, mat: Materializer): Clients =
    new Clients(
      store = store,
      clientSecretConfig = clientSecretConfig
    )
}
