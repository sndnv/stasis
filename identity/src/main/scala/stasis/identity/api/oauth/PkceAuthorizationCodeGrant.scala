package stasis.identity.api.oauth

import scala.concurrent.ExecutionContext
import org.apache.pekko.actor.typed.{ActorSystem, SpawnProtocol}
import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{Format, Json}
import stasis.identity.api.Formats._
import stasis.identity.api.oauth.directives.AuthDirectives
import stasis.identity.api.oauth.setup.{Config, Providers}
import stasis.identity.model.clients.Client
import stasis.identity.model.codes.AuthorizationCode
import stasis.identity.model.errors.{AuthorizationError, TokenError}
import stasis.identity.model.tokens._
import stasis.identity.model.{ChallengeMethod, GrantType, ResponseType, Seconds}

class PkceAuthorizationCodeGrant(
  override val config: Config,
  override val providers: Providers
)(implicit system: ActorSystem[SpawnProtocol.Command])
    extends AuthDirectives {
  import PkceAuthorizationCodeGrant._
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  override implicit protected def ec: ExecutionContext = system.executionContext
  override protected val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private val authorizationParams = parameters(
    "response_type".as[ResponseType],
    "client_id".as[Client.Id],
    "redirect_uri".as[String].?,
    "scope".as[String].?,
    "state".as[String],
    "code_challenge".as[String],
    "code_challenge_method".as[ChallengeMethod].?
  )

  private val tokenParams = parameters(
    "grant_type".as[GrantType],
    "code".as[AuthorizationCode],
    "redirect_uri".as[String].?,
    "client_id".as[Client.Id],
    "code_verifier".as[String]
  ).or(
    formFields(
      "grant_type".as[GrantType],
      "code".as[AuthorizationCode],
      "redirect_uri".as[String].?,
      "client_id".as[Client.Id],
      "code_verifier".as[String]
    )
  )

  def authorization(): Route =
    get {
      (authorizationParams.as(AuthorizationRequest) & parameter("no_redirect".as[Boolean] ? false)) {
        case (request, noRedirect) =>
          retrieveClient(request.client_id) {
            case client if request.redirect_uri.forall(_ == client.redirectUri) =>
              val redirectUri = Uri(request.redirect_uri.getOrElse(client.redirectUri))

              (authenticateResourceOwner(redirectUri, request.state, noRedirect) & extractApiAudience(request.scope)) {
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

                    val response = AuthorizationResponse(code, request.state, scope)

                    if (noRedirect) {
                      discardEntity & complete(
                        StatusCodes.OK,
                        AuthorizationResponseWithRedirectUri(
                          response = response,
                          responseRedirectUri = redirectUri.withQuery(response.asQuery)
                        )
                      )
                    } else {
                      discardEntity & redirect(
                        redirectUri.withQuery(response.asQuery),
                        StatusCodes.Found
                      )
                    }
                  }
              }

            case client =>
              log.warnN(
                "Redirect URI [{}] for client [{}] did not match URI provided in request: [{}]",
                client.redirectUri,
                client.id,
                request.redirect_uri
              )

              discardEntity {
                complete(
                  StatusCodes.BadRequest,
                  AuthorizationError.InvalidRequest(withState = request.state): AuthorizationError
                )
              }
          }
      }
    }

  def token(): Route =
    post {
      tokenParams.as(AccessTokenRequest) { request =>
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
                          access_token = accessToken.token,
                          token_type = TokenType.Bearer,
                          expires_in = accessToken.expiration,
                          refresh_token = refreshToken
                        )
                      )
                    }
                }
              }
            }

          case client =>
            log.warnN(
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
                TokenError.InvalidRequest: TokenError
              )
            }
        }
      }
    }
}

object PkceAuthorizationCodeGrant {
  def apply(
    config: Config,
    providers: Providers
  )(implicit system: ActorSystem[SpawnProtocol.Command]): PkceAuthorizationCodeGrant =
    new PkceAuthorizationCodeGrant(
      config = config,
      providers = providers
    )

  implicit val accessTokenResponseFormat: Format[AccessTokenResponse] =
    Json.format[AccessTokenResponse]

  implicit val authorizationResponseWithRedirectUriFormat: Format[AuthorizationResponseWithRedirectUri] =
    Json.format[AuthorizationResponseWithRedirectUri]

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

  final case class AuthorizationResponseWithRedirectUri(
    code: AuthorizationCode,
    state: String,
    scope: Option[String],
    redirect_uri: String
  )

  object AuthorizationResponseWithRedirectUri {
    def apply(response: AuthorizationResponse, responseRedirectUri: Uri): AuthorizationResponseWithRedirectUri =
      AuthorizationResponseWithRedirectUri(
        code = response.code,
        state = response.state,
        scope = response.scope,
        redirect_uri = responseRedirectUri.toString
      )
  }

  final case class AccessTokenResponse(
    access_token: AccessToken,
    token_type: TokenType,
    expires_in: Seconds,
    refresh_token: Option[RefreshToken]
  )
}
