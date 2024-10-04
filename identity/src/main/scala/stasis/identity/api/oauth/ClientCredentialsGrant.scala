package stasis.identity.api.oauth

import scala.concurrent.ExecutionContext

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.headers.CacheDirectives
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import play.api.libs.json.Format
import play.api.libs.json.Json

import stasis.identity.api.Formats._
import stasis.identity.api.oauth.directives.AuthDirectives
import stasis.identity.api.oauth.setup.Config
import stasis.identity.api.oauth.setup.Providers
import stasis.identity.model.GrantType
import stasis.identity.model.Seconds
import stasis.identity.model.tokens.AccessToken
import stasis.identity.model.tokens.TokenType

class ClientCredentialsGrant(
  override val config: Config,
  override val providers: Providers
)(implicit system: ActorSystem[Nothing])
    extends AuthDirectives {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  import ClientCredentialsGrant._

  override implicit protected def ec: ExecutionContext = system.executionContext
  override protected val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private val tokenParams =
    parameters(
      "grant_type".as[GrantType],
      "scope".as[String].?
    )
      .or(
        formFields(
          "grant_type".as[GrantType],
          "scope".as[String].?
        )
      )

  def token(): Route =
    post {
      tokenParams.as(AccessTokenRequest) { request =>
        (authenticateClient() & extractClientAudience(request.scope)) { (client, audience) =>
          generateAccessToken(client, audience) { accessToken =>
            val scope = clientAudienceToScope(audience)

            log.debugN("Successfully generated access token for client [{}]", client.id)

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
  )(implicit system: ActorSystem[Nothing]): ClientCredentialsGrant =
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
