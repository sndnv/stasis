package stasis.identity.api.oauth

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import play.api.libs.json.{Format, Json}
import stasis.identity.api.Formats._
import stasis.identity.api.oauth.directives.AuthDirectives
import stasis.identity.api.oauth.setup.Providers
import stasis.identity.model.realms.Realm
import stasis.identity.model.tokens._
import stasis.identity.model.{GrantType, Seconds}

import scala.concurrent.ExecutionContext

class ResourceOwnerPasswordCredentialsGrant(
  override val providers: Providers
)(implicit system: ActorSystem, override val mat: Materializer)
    extends AuthDirectives {
  import ResourceOwnerPasswordCredentialsGrant._
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  override implicit protected def ec: ExecutionContext = system.dispatcher
  override protected def log: LoggingAdapter = Logging(system, this.getClass.getName)

  def token(realm: Realm): Route =
    post {
      parameters(
        "grant_type".as[GrantType],
        "username".as[String],
        "password".as[String],
        "scope".as[String].?
      ).as(AccessTokenRequest) { request =>
        authenticateClient(realm) { client =>
          authenticateResourceOwner(request.username, request.password) { owner =>
            extractApiAudience(realm.id, request.scope) { audience =>
              val scope = apiAudienceToScope(audience)

              (generateAccessToken(owner, audience) & generateRefreshToken(realm, client.id, owner, scope)) {
                (accessToken, refreshToken) =>
                  log.debug(
                    "Realm [{}]: Successfully generated {} for client [{}]",
                    realm.id,
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
                        headers.`Content-Type`(ContentTypes.`application/json`),
                        headers.`Cache-Control`(headers.CacheDirectives.`no-store`)
                      ),
                      AccessTokenResponse(
                        access_token = accessToken,
                        token_type = TokenType.Bearer,
                        expires_in = client.tokenExpiration,
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

object ResourceOwnerPasswordCredentialsGrant {
  implicit val accessTokenResponseFormat: Format[AccessTokenResponse] = Json.format[AccessTokenResponse]

  final case class AccessTokenRequest(
    grant_type: GrantType,
    username: String,
    password: String,
    scope: Option[String]
  ) {
    require(grant_type == GrantType.Password, "grant type must be 'password'")
    require(username.nonEmpty, "username must not be empty")
    require(password.nonEmpty, "password must not be empty")
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
