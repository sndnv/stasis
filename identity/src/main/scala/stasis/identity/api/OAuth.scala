package stasis.identity.api

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.Materializer
import stasis.identity.api.manage.directives.RealmExtraction
import stasis.identity.api.oauth._
import stasis.identity.api.oauth.setup.Providers
import stasis.identity.model.realms.RealmStoreView

class OAuth(
  providers: Providers
)(implicit system: ActorSystem, override val mat: Materializer)
    extends RealmExtraction {

  override protected def log: LoggingAdapter = Logging(system, this.getClass.getName)
  override protected def realmStore: RealmStoreView = providers.realmStore.view

  private val authorizationCodeGrant = new AuthorizationCodeGrant(providers)
  private val pkceAuthorizationCodeGrant = new PkceAuthorizationCodeGrant(providers)
  private val clientCredentialsGrant = new ClientCredentialsGrant(providers)
  private val implicitGrant = new ImplicitGrant(providers)
  private val refreshTokenGrant = new RefreshTokenGrant(providers)
  private val passwordCredentialsGrant = new ResourceOwnerPasswordCredentialsGrant(providers)

  def routes: Route =
    concat(
      pathPrefix(Segment) { realmId =>
        extractRealm(realmId) {
          realm =>
            concat(
              path("authorization") {
                parameter("response_type".as[String]) {
                  case "code" =>
                    parameter("code_challenge".as[String].?) {
                      case Some(_) => pkceAuthorizationCodeGrant.authorization(realm)
                      case None    => authorizationCodeGrant.authorization(realm)
                    }

                  case "token" =>
                    implicitGrant.authorization(realm)

                  case responseType =>
                    val message = s"Realm [$realmId]: The request includes an invalid response type: [$responseType]"
                    log.warning(message)
                    discardEntity & complete(StatusCodes.BadRequest, message)
                }
              },
              path("token") {
                parameter("grant_type".as[String]) {
                  case "authorization_code" =>
                    parameter("code_verifier".as[String].?) {
                      case Some(_) => pkceAuthorizationCodeGrant.token(realm)
                      case None    => authorizationCodeGrant.token(realm)
                    }

                  case "client_credentials" =>
                    clientCredentialsGrant.token(realm)

                  case "refresh_token" =>
                    refreshTokenGrant.token(realm)

                  case "password" =>
                    passwordCredentialsGrant.token(realm)

                  case grantType =>
                    val message = s"Realm [$realmId]: The request includes an invalid grant type: [$grantType]"
                    log.warning(message)
                    discardEntity & complete(StatusCodes.BadRequest, message)
                }
              }
            )
        }
      }
    )
}
