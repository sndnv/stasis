package stasis.identity.api

import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server._
import org.slf4j.{Logger, LoggerFactory}
import stasis.identity.api.manage._
import stasis.identity.api.manage.directives.{UserAuthentication, UserAuthorization}
import stasis.identity.api.manage.setup.{Config, Providers}
import stasis.identity.authentication.manage.ResourceOwnerAuthenticator

class Manage(
  providers: Providers,
  config: Config
) extends UserAuthentication
    with UserAuthorization {
  import Manage._

  override protected val realm: String = config.realm

  override protected val log: Logger = LoggerFactory.getLogger(this.getClass.getName)
  override protected def authenticator: ResourceOwnerAuthenticator = providers.ownerAuthenticator

  private val apis = Apis(providers.apiStore)
  private val clients = Clients(providers.clientStore, config.clientSecrets)
  private val codes = Codes(providers.codeStore)
  private val owners = Owners(providers.ownerStore, config.ownerSecrets)
  private val tokens = Tokens(providers.tokenStore)

  def routes: Route =
    authenticate() { user =>
      concat(
        pathPrefix("codes") {
          authorize(user, Scopes.ManageCodes) {
            codes.routes(user.username)
          }
        },
        pathPrefix("tokens") {
          authorize(user, Scopes.ManageTokens) {
            tokens.routes(user.username)
          }
        },
        pathPrefix("apis") {
          authorize(user, Scopes.ManageApis) {
            apis.routes(user.username)
          }
        },
        pathPrefix("clients") {
          authorize(user, Scopes.ManageClients) {
            clients.routes(user.username)
          }
        },
        pathPrefix("owners") {
          authorize(user, Scopes.ManageOwners) {
            owners.routes(user.username)
          }
        }
      )
    }
}

object Manage {
  object Scopes {
    final val ManageCodes: String = "manage:codes"
    final val ManageTokens: String = "manage:tokens"
    final val ManageApis: String = "manage:apis"
    final val ManageClients: String = "manage:clients"
    final val ManageOwners: String = "manage:owners"
  }

  def apply(providers: Providers, config: Config): Manage =
    new Manage(providers = providers, config = config)
}
