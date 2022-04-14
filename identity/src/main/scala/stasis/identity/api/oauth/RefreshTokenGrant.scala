package stasis.identity.api.oauth

import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.LoggerOps
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{Format, Json}
import stasis.identity.api.Formats._
import stasis.identity.api.oauth.directives.AuthDirectives
import stasis.identity.api.oauth.setup.{Config, Providers}
import stasis.identity.model.tokens._
import stasis.identity.model.{GrantType, Seconds}

import scala.concurrent.ExecutionContext

class RefreshTokenGrant(
  override val config: Config,
  override val providers: Providers
)(implicit system: ActorSystem)
    extends AuthDirectives {
  import RefreshTokenGrant._
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  override implicit protected def ec: ExecutionContext = system.dispatcher
  override protected val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private val tokenParams = parameters(
    "grant_type".as[GrantType],
    "refresh_token".as[RefreshToken],
    "scope".as[String].?
  ).or(
    formFields(
      "grant_type".as[GrantType],
      "refresh_token".as[RefreshToken],
      "scope".as[String].?
    )
  )

  def token(): Route =
    post {
      tokenParams.as(AccessTokenRequest) { request =>
        authenticateClient() { client =>
          consumeRefreshToken(client.id, request.scope, request.refresh_token) { owner =>
            extractApiAudience(request.scope) { audience =>
              val scope = apiAudienceToScope(audience)

              (generateAccessToken(owner, audience) & generateRefreshToken(client.id, owner, scope)) {
                (accessToken, refreshToken) =>
                  log.debugN(
                    "Successfully generated {} for client [{}]",
                    refreshToken match {
                      case Some(_) => "access and refresh tokens"
                      case None    => "access token"
                    },
                    client.id
                  )

                  discardEntity {
                    complete(
                      StatusCodes.OK,
                      List[HttpHeader](
                        headers.`Cache-Control`(headers.CacheDirectives.`no-store`)
                      ),
                      AccessTokenResponse(
                        access_token = accessToken.token,
                        token_type = TokenType.Bearer,
                        expires_in = accessToken.expiration,
                        refresh_token = refreshToken,
                        scope = scope
                      )
                    )
                  }
              }
            }
          }
        }
      }
    }
}

object RefreshTokenGrant {
  def apply(
    config: Config,
    providers: Providers
  )(implicit system: ActorSystem): RefreshTokenGrant =
    new RefreshTokenGrant(
      config = config,
      providers = providers
    )

  implicit val accessTokenResponseFormat: Format[AccessTokenResponse] = Json.format[AccessTokenResponse]

  final case class AccessTokenRequest(
    grant_type: GrantType,
    refresh_token: RefreshToken,
    scope: Option[String]
  ) {
    require(grant_type == GrantType.RefreshToken, "grant type must be 'refresh_token'")
    require(refresh_token.value.nonEmpty, "refresh token must not be empty")
    require(scope.forall(_.nonEmpty), "scope must not be empty")
  }

  final case class AccessTokenResponse(
    access_token: AccessToken,
    token_type: TokenType,
    expires_in: Seconds,
    refresh_token: Option[RefreshToken],
    scope: Option[String]
  )
}
