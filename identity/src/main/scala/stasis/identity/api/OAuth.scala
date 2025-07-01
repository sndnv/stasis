package stasis.identity.api

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server._
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.identity.api.oauth._
import stasis.identity.api.oauth.setup.Config
import stasis.identity.api.oauth.setup.Providers
import io.github.sndnv.layers.api.directives.EntityDiscardingDirectives

class OAuth(
  config: Config,
  providers: Providers
)(implicit system: ActorSystem[Nothing])
    extends EntityDiscardingDirectives {

  private val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private val authorizationCodeGrant = AuthorizationCodeGrant(config, providers)
  private val pkceAuthorizationCodeGrant = PkceAuthorizationCodeGrant(config, providers)
  private val clientCredentialsGrant = ClientCredentialsGrant(config, providers)
  private val implicitGrant = ImplicitGrant(config, providers)
  private val refreshTokenGrant = RefreshTokenGrant(config, providers)
  private val passwordCredentialsGrant = ResourceOwnerPasswordCredentialsGrant(config, providers)

  def routes: Route =
    concat(
      path("authorization") {
        parameter("response_type".as[String]) {
          case "code" =>
            parameter("code_challenge".as[String].?) {
              case Some(_) => pkceAuthorizationCodeGrant.authorization()
              case None    => authorizationCodeGrant.authorization()
            }

          case "token" =>
            implicitGrant.authorization()

          case responseType =>
            val message = s"The request includes an invalid response type: [$responseType]"
            log.warnN(message)
            discardEntity & complete(StatusCodes.BadRequest, message)
        }
      },
      path("token") {
        parameter("grant_type".as[String]).or(formField("grant_type".as[String])) {
          case "authorization_code" =>
            parameter("code_verifier".as[String].?) { verifierParam =>
              formField("code_verifier".as[String].?) { verifierField =>
                verifierParam.orElse(verifierField) match {
                  case Some(_) => pkceAuthorizationCodeGrant.token()
                  case None    => authorizationCodeGrant.token()
                }
              }
            }

          case "client_credentials" =>
            clientCredentialsGrant.token()

          case "refresh_token" =>
            refreshTokenGrant.token()

          case "password" =>
            passwordCredentialsGrant.token()

          case grantType =>
            val message = s"The request includes an invalid grant type: [$grantType]"
            log.warnN(message)
            discardEntity & complete(StatusCodes.BadRequest, message)
        }
      }
    )
}

object OAuth {
  def apply(
    config: Config,
    providers: Providers
  )(implicit system: ActorSystem[Nothing]): OAuth =
    new OAuth(
      config = config,
      providers = providers
    )
}
