package stasis.identity.api.manage

import java.time.Instant

import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server._
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.identity.api.Formats._
import stasis.identity.api.manage.requests.CreateClient
import stasis.identity.api.manage.requests.UpdateClient
import stasis.identity.api.manage.requests.UpdateClientCredentials
import stasis.identity.api.manage.responses.CreatedClient
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.secrets.Secret
import stasis.identity.persistence.clients.ClientStore
import io.github.sndnv.layers.api.directives.EntityDiscardingDirectives

class Clients(
  store: ClientStore,
  clientSecretConfig: Secret.ClientConfig
) extends EntityDiscardingDirectives {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  private val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private implicit val secretConfig: Secret.ClientConfig = clientSecretConfig

  def routes(user: ResourceOwner.Id): Route =
    concat(
      pathEndOrSingleSlash {
        concat(
          get {
            onSuccess(store.all) { clients =>
              log.debugN("User [{}] successfully retrieved [{}] clients", user, clients.size)
              discardEntity & complete(clients)
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
            onSuccess(store.all) { clients =>
              val matchingClients = clients.filter { client =>
                client.subject.contains(subject) || client.id.toString == subject
              }

              log.debugN("User [{}] found [{}] clients for subject [{}]", user, matchingClients.size, subject)
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

                    onSuccess(store.put(client.copy(secret = secret, salt = salt, updated = Instant.now()))) { _ =>
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
                            active = request.active,
                            updated = Instant.now()
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
  ): Clients =
    new Clients(
      store = store,
      clientSecretConfig = clientSecretConfig
    )
}
