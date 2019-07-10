package stasis.identity.api.manage

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.Materializer
import stasis.identity.api.Formats._
import stasis.identity.api.manage.directives.RealmValidation
import stasis.identity.api.manage.requests.{CreateClient, UpdateClient, UpdateClientCredentials}
import stasis.identity.api.manage.responses.CreatedClient
import stasis.identity.model.clients.{Client, ClientStore}
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.realms.Realm
import stasis.identity.model.secrets.Secret

import scala.concurrent.ExecutionContext

class Clients(
  store: ClientStore,
  clientSecretConfig: Secret.ClientConfig
)(implicit system: ActorSystem, override val mat: Materializer)
    extends RealmValidation[Client] {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  private implicit val ec: ExecutionContext = system.dispatcher
  protected def log: LoggingAdapter = Logging(system, this.getClass.getName)

  override implicit protected def extractor: RealmValidation.Extractor[Client] = _.realm

  private implicit val secretConfig: Secret.ClientConfig = clientSecretConfig

  def routes(user: ResourceOwner.Id, realm: Realm.Id): Route =
    concat(
      pathEndOrSingleSlash {
        concat(
          get {
            filterRealm(realm, store.clients) { clients =>
              log.info("Realm [{}]: User [{}] successfully retrieved [{}] clients", realm, user, clients.size)
              discardEntity & complete(clients.values)
            }
          },
          post {
            entity(as[CreateClient]) { request =>
              val client = request.toClient(realm)
              onSuccess(store.put(client)) { _ =>
                log.info("Realm [{}]: User [{}] successfully created client [{}]", realm, user, client.id)
                complete(CreatedClient(client.id))
              }
            }
          }
        )
      },
      pathPrefix(JavaUUID) { clientId =>
        validateRealm(realm, store.get(clientId)) {
          client =>
            concat(
              path("credentials") {
                put {
                  entity(as[UpdateClientCredentials]) { request =>
                    val (secret, salt) = request.toSecret()

                    onSuccess(store.put(client.copy(secret = secret, salt = salt))) { _ =>
                      log.info(
                        "Realm [{}]: User [{}] successfully updated credentials for client [{}]",
                        realm,
                        user,
                        clientId
                      )
                      complete(StatusCodes.OK)
                    }
                  }
                }
              },
              pathEndOrSingleSlash {
                concat(
                  get {
                    log.info("Realm [{}]: User [{}] successfully retrieved client [{}]", realm, user, clientId)
                    discardEntity & complete(client)
                  },
                  put {
                    entity(as[UpdateClient]) { request =>
                      onSuccess(
                        store.put(
                          client.copy(
                            allowedScopes = request.allowedScopes,
                            tokenExpiration = request.tokenExpiration,
                            active = request.active
                          )
                        )
                      ) { _ =>
                        log.info("Realm [{}]: User [{}] successfully updated client [{}]", realm, user, clientId)
                        complete(StatusCodes.OK)
                      }
                    }
                  },
                  delete {
                    onSuccess(store.delete(clientId)) { _ =>
                      log.info("Realm [{}]: User [{}] successfully deleted client [{}]", realm, user, clientId)
                      discardEntity & complete(StatusCodes.OK)
                    }
                  }
                )
              }
            )
        }
      }
    )
}
