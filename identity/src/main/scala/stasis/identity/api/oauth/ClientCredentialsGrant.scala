package stasis.identity.api.oauth

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.CacheDirectives
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import play.api.libs.json.{Format, Json}
import stasis.identity.api.Formats._
import stasis.identity.api.oauth.directives.AuthDirectives
import stasis.identity.api.oauth.setup.{Config, Providers}
import stasis.identity.model.tokens.{AccessToken, TokenType}
import stasis.identity.model.{GrantType, Seconds}

import scala.concurrent.ExecutionContext

class ClientCredentialsGrant(
  override val config: Config,
  override val providers: Providers
)(implicit system: ActorSystem, override val mat: Materializer)
    extends AuthDirectives {
  import ClientCredentialsGrant._
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  override implicit protected def ec: ExecutionContext = system.dispatcher
  override protected def log: LoggingAdapter = Logging(system, this.getClass.getName)

  def token(): Route =
    post {
      parameters(
        "grant_type".as[GrantType],
        "scope".as[String].?
      ).as(AccessTokenRequest) { request =>
        (authenticateClient() & extractClientAudience(request.scope)) { (client, audience) =>
          generateAccessToken(client, audience) { accessToken =>
            val scope = clientAudienceToScope(audience)

            log.debug("Successfully generated access token for client [{}]", client.id)

            discardEntity {
              complete(
                StatusCodes.OK,
                List[HttpHeader](
                  headers.`Cache-Control`(CacheDirectives.`no-store`)
                ),
                AccessTokenResponse(
                  access_token = accessToken.token,
                  token_type = TokenType.Bearer,
                  expires_in = accessToken.expiration,
                  scope = scope
                )
              )
            }
          }
        }
      }
    }
}

object ClientCredentialsGrant {
  def apply(
    config: Config,
    providers: Providers
  )(implicit system: ActorSystem, mat: Materializer): ClientCredentialsGrant =
    new ClientCredentialsGrant(
      config = config,
      providers = providers
    )

  implicit val accessTokenResponseFormat: Format[AccessTokenResponse] = Json.format[AccessTokenResponse]

  final case class AccessTokenRequest(
    grant_type: GrantType,
    scope: Option[String]
  ) {
    require(grant_type == GrantType.ClientCredentials, "grant type must be 'client_credentials'")
    require(scope.forall(_.nonEmpty), "scope must not be empty")
  }

  final case class AccessTokenResponse(
    access_token: AccessToken,
    token_type: TokenType,
    expires_in: Seconds,
    scope: Option[String]
  )
}
