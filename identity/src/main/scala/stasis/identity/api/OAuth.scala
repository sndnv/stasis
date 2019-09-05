package stasis.identity.api

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.Materializer
import stasis.core.api.directives.EntityDiscardingDirectives
import stasis.identity.api.oauth._
import stasis.identity.api.oauth.setup.{Config, Providers}

class OAuth(
  config: Config,
  providers: Providers
)(implicit system: ActorSystem, override val mat: Materializer)
    extends EntityDiscardingDirectives {

  private val log: LoggingAdapter = Logging(system, this.getClass.getName)

  private val authorizationCodeGrant = new AuthorizationCodeGrant(config, providers)
  private val pkceAuthorizationCodeGrant = new PkceAuthorizationCodeGrant(config, providers)
  private val clientCredentialsGrant = new ClientCredentialsGrant(config, providers)
  private val implicitGrant = new ImplicitGrant(config, providers)
  private val refreshTokenGrant = new RefreshTokenGrant(config, providers)
  private val passwordCredentialsGrant = new ResourceOwnerPasswordCredentialsGrant(config, providers)

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
            log.warning(message)
            discardEntity & complete(StatusCodes.BadRequest, message)
        }
      },
      path("token") {
        parameter("grant_type".as[String]) {
          case "authorization_code" =>
            parameter("code_verifier".as[String].?) {
              case Some(_) => pkceAuthorizationCodeGrant.token()
              case None    => authorizationCodeGrant.token()
            }

          case "client_credentials" =>
            clientCredentialsGrant.token()

          case "refresh_token" =>
            refreshTokenGrant.token()

          case "password" =>
            passwordCredentialsGrant.token()

          case grantType =>
            val message = s"The request includes an invalid grant type: [$grantType]"
            log.warning(message)
            discardEntity & complete(StatusCodes.BadRequest, message)
        }
      }
    )
}
