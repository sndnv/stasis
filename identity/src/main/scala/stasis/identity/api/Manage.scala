package stasis.identity.api

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.Materializer
import stasis.identity.api.manage._
import stasis.identity.api.manage.directives.{RealmExtraction, UserAuthentication, UserAuthorization}
import stasis.identity.api.manage.setup.{Config, Providers}
import stasis.identity.authentication.manage.ResourceOwnerAuthenticator
import stasis.identity.model.realms.{Realm, RealmStoreView}

class Manage(
  providers: Providers,
  config: Config
)(implicit system: ActorSystem, override val mat: Materializer)
    extends RealmExtraction
    with UserAuthentication
    with UserAuthorization {
  import Manage._

  override protected def log: LoggingAdapter = Logging(system, this.getClass.getName)
  override protected def authenticator: ResourceOwnerAuthenticator = providers.ownerAuthenticator
  override protected def realmStore: RealmStoreView = providers.realmStore.view

  private val apis = new Apis(providers.apiStore)
  private val clients = new Clients(providers.clientStore, config.clientSecrets)
  private val codes = new Codes(providers.codeStore)
  private val owners = new Owners(providers.ownerStore, config.ownerSecrets)
  private val realms = new Realms(providers.realmStore)
  private val tokens = new Tokens(providers.tokenStore)

  def routes: Route =
    concat(
      pathPrefix(Segment) {
        case Realm.Master =>
          authenticate(Realm.Master) { user =>
            authorize(user, Realm.Master, Scopes.ManageMaster) {
              concat(
                pathPrefix("realms") {
                  realms.routes(user.username)
                },
                pathPrefix("codes") {
                  codes.routes(user.username)
                },
                pathPrefix("tokens") {
                  tokens.routes(user.username)
                }
              )
            }
          }

        case realmId: Realm.Id =>
          extractRealm(realmId) { realm =>
            authenticate(realmId) { user =>
              concat(
                pathPrefix("apis") {
                  authorize(user, realm.id, Scopes.ManageApis) {
                    apis.routes(user.username, realmId)
                  }
                },
                pathPrefix("clients") {
                  authorize(user, realm.id, Scopes.ManageClients) {
                    clients.routes(user.username, realmId)
                  }
                },
                pathPrefix("owners") {
                  authorize(user, realm.id, Scopes.ManageOwners) {
                    owners.routes(user.username, realmId)
                  }
                }
              )
            }
          }
      }
    )
}

object Manage {
  object Scopes {
    final val ManageMaster: String = "manage:master"
    final val ManageApis: String = "manage:apis"
    final val ManageClients: String = "manage:clients"
    final val ManageOwners: String = "manage:owners"
  }
}
