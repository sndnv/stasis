package stasis.identity.api.manage

import akka.actor.typed.scaladsl.LoggerOps
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.Materializer
import org.slf4j.{Logger, LoggerFactory}
import stasis.core.api.directives.EntityDiscardingDirectives
import stasis.identity.api.Formats._
import stasis.identity.api.manage.requests.{CreateClient, UpdateClient, UpdateClientCredentials}
import stasis.identity.api.manage.responses.CreatedClient
import stasis.identity.model.clients.ClientStore
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.secrets.Secret

class Clients(
  store: ClientStore,
  clientSecretConfig: Secret.ClientConfig
)(implicit override val mat: Materializer)
    extends EntityDiscardingDirectives {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  private val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private implicit val secretConfig: Secret.ClientConfig = clientSecretConfig

  def routes(user: ResourceOwner.Id): Route =
    concat(
      pathEndOrSingleSlash {
        concat(
          get {
            onSuccess(store.clients) { clients =>
              log.debugN("User [{}] successfully retrieved [{}] clients", user, clients.size)
              discardEntity & complete(clients.values)
            }
          },
          post {
            entity(as[CreateClient]) { request =>
              val client = request.toClient
              onSuccess(store.put(client)) { _ =>
                log.debugN("User [{}] successfully created client [{}]", user, client.id)
                complete(CreatedClient(client.id))
              }
            }
          }
        )
      },
      pathPrefix("search") {
        path("by-subject" / Segment) { subject =>
          get {
            onSuccess(store.clients) { clients =>
              val matchingClients = clients.values.filter { client =>
                client.subject.contains(subject) || client.id.toString == subject
              }

              log.debugN("User [{}] found [{}] clients for subject [{}]", user, clients.size, subject)
              discardEntity & complete(matchingClients)
            }
          }
        }
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
                      log.debugN("User [{}] successfully updated credentials for client [{}]", user, clientId)
                      complete(StatusCodes.OK)
                    }
                  }
                }
              },
              pathEndOrSingleSlash {
                concat(
                  get {
                    log.debugN("User [{}] successfully retrieved client [{}]", user, clientId)
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
                        log.debugN("User [{}] successfully updated client [{}]", user, clientId)
                        complete(StatusCodes.OK)
                      }
                    }
                  },
                  delete {
                    onSuccess(store.delete(clientId)) { _ =>
                      log.debugN("User [{}] successfully deleted client [{}]", user, clientId)
                      discardEntity & complete(StatusCodes.OK)
                    }
                  }
                )
              }
            )

          case None =>
            log.warnN("User [{}] made request for client [{}] but it was not found", user, clientId)
            discardEntity & complete(StatusCodes.NotFound)
        }
      }
    )
}

object Clients {
  def apply(
    store: ClientStore,
    clientSecretConfig: Secret.ClientConfig
  )(implicit mat: Materializer): Clients =
    new Clients(
      store = store,
      clientSecretConfig = clientSecretConfig
    )
}
