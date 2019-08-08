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
import stasis.identity.api.oauth.setup.{Config, Providers}
import stasis.identity.model.clients.Client
import stasis.identity.model.codes.AuthorizationCode
import stasis.identity.model.errors.TokenError
import stasis.identity.model.tokens._
import stasis.identity.model.{ChallengeMethod, GrantType, ResponseType, Seconds}

import scala.concurrent.ExecutionContext

class PkceAuthorizationCodeGrant(
  override val config: Config,
  override val providers: Providers
)(implicit system: ActorSystem, override val mat: Materializer)
    extends AuthDirectives {
  import PkceAuthorizationCodeGrant._
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  override implicit protected def ec: ExecutionContext = system.dispatcher
  override protected def log: LoggingAdapter = Logging(system, this.getClass.getName)

  def authorization(): Route =
    get {
      parameters(
        "response_type".as[ResponseType],
        "client_id".as[Client.Id],
        "redirect_uri".as[String].?,
        "scope".as[String].?,
        "state".as[String],
        "code_challenge".as[String],
        "code_challenge_method".as[ChallengeMethod].?
      ).as(AuthorizationRequest) { request =>
        retrieveClient(request.client_id) {
          case client if request.redirect_uri.forall(_ == client.redirectUri) =>
            val redirectUri = Uri(request.redirect_uri.getOrElse(client.redirectUri))

            (authenticateResourceOwner(redirectUri, request.state) & extractApiAudience(request.scope)) {
              (owner, audience) =>
                val scope = apiAudienceToScope(audience)

                generateAuthorizationCode(
                  client = client.id,
                  redirectUri = redirectUri,
                  state = request.state,
                  owner = owner,
                  scope = scope,
                  challenge = request.code_challenge,
                  challengeMethod = request.code_challenge_method
                ) { code =>
                  log.debug(
                    "Successfully generated authorization code for client [{}] and owner [{}]",
                    client.id,
                    owner.username
                  )

                  discardEntity {
                    redirect(
                      redirectUri.withQuery(AuthorizationResponse(code, request.state, scope).asQuery),
                      StatusCodes.Found
                    )
                  }
                }
            }

          case client =>
            log.warning(
              "Redirect URI [{}] for client [{}] did not match URI provided in request: [{}]",
              client.redirectUri,
              client.id,
              request.redirect_uri
            )

            discardEntity {
              complete(
                StatusCodes.BadRequest,
                "The request has missing, invalid or mismatching redirection URI and/or client identifier"
              )
            }
        }
      }
    }

  def token(): Route =
    post {
      parameters(
        "grant_type".as[GrantType],
        "code".as[AuthorizationCode],
        "redirect_uri".as[String].?,
        "client_id".as[Client.Id],
        "code_verifier".as[String]
      ).as(AccessTokenRequest) { request =>
        retrieveClient(request.client_id) {
          case client if client.id == request.client_id && request.redirect_uri.forall(_ == client.redirectUri) =>
            consumeAuthorizationCode(client.id, request.code, request.code_verifier) { (owner, scope) =>
              extractApiAudience(scope) { audience =>
                val scope = apiAudienceToScope(audience)

                (generateAccessToken(owner, audience) & generateRefreshToken(client.id, owner, scope)) {
                  (accessToken, refreshToken) =>
                    log.debug(
                      "Successfully generated {} for client [{}] and owner [{}]",
                      refreshToken match {
                        case Some(_) => "access and refresh tokens"
                        case None    => "access token"
                      },
                      client.id,
                      owner.username
                    )

                    discardEntity {
                      complete(
                        StatusCodes.OK,
                        List[HttpHeader](
                          headers.`Cache-Control`(headers.CacheDirectives.`no-store`)
                        ),
                        AccessTokenResponse(
                          access_token = accessToken,
                          token_type = TokenType.Bearer,
                          expires_in = client.tokenExpiration,
                          refresh_token = refreshToken
                        )
                      )
                    }
                }
              }
            }

          case client =>
            log.warning(
              "Encountered mismatched client identifiers (expected [{}], found [{}]) " +
                "and/or redirect URIs (expected [{}], found [{}])",
              client.id,
              request.client_id,
              client.redirectUri,
              request.redirect_uri
            )

            discardEntity {
              complete(
                StatusCodes.BadRequest,
                TokenError.InvalidRequest
              )
            }
        }
      }
    }
}

object PkceAuthorizationCodeGrant {
  implicit val accessTokenResponseFormat: Format[AccessTokenResponse] = Json.format[AccessTokenResponse]

  final case class AuthorizationRequest(
    response_type: ResponseType,
    client_id: Client.Id,
    redirect_uri: Option[String],
    scope: Option[String],
    state: String,
    code_challenge: String,
    code_challenge_method: Option[ChallengeMethod]
  ) {
    require(response_type == ResponseType.Code, "response type must be 'code'")
    require(redirect_uri.forall(_.nonEmpty), "redirect URI must not be empty")
    require(scope.forall(_.nonEmpty), "scope must not be empty")
    require(state.nonEmpty, "state must not be empty")
    require(code_challenge.nonEmpty, "code challenge must not be empty")
  }

  final case class AccessTokenRequest(
    grant_type: GrantType,
    code: AuthorizationCode,
    redirect_uri: Option[String],
    client_id: Client.Id,
    code_verifier: String
  ) {
    require(grant_type == GrantType.AuthorizationCode, "grant type must be 'authorization_code'")
    require(code.value.nonEmpty, "code must not be empty")
    require(redirect_uri.forall(_.nonEmpty), "redirect URI must not be empty")
    require(code_verifier.nonEmpty, "code verifier must not be empty")
  }

  final case class AuthorizationResponse(
    code: AuthorizationCode,
    state: String,
    scope: Option[String]
  ) {
    def asQuery: Uri.Query =
      Uri.Query(
        scope.foldLeft(
          Map(
            "code" -> code.value,
            "state" -> state
          )
        ) { case (baseParams, actualScope) => baseParams + ("scope" -> actualScope) }
      )
  }

  final case class AccessTokenResponse(
    access_token: AccessToken,
    token_type: TokenType,
    expires_in: Seconds,
    refresh_token: Option[RefreshToken]
  )
}