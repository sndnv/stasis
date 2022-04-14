package stasis.identity.api.oauth

import scala.concurrent.ExecutionContext

import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.LoggerOps
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.slf4j.{Logger, LoggerFactory}
import stasis.identity.api.Formats._
import stasis.identity.api.oauth.directives.AuthDirectives
import stasis.identity.api.oauth.setup.{Config, Providers}
import stasis.identity.model.clients.Client
import stasis.identity.model.errors.AuthorizationError
import stasis.identity.model.tokens.{AccessToken, TokenType}
import stasis.identity.model.{ResponseType, Seconds}

class ImplicitGrant(
  override val config: Config,
  override val providers: Providers
)(implicit system: ActorSystem)
    extends AuthDirectives {
  import ImplicitGrant._
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  override implicit protected def ec: ExecutionContext = system.dispatcher
  override protected val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private val authorizationParams = parameters(
    "response_type".as[ResponseType],
    "client_id".as[Client.Id],
    "redirect_uri".as[String].?,
    "scope".as[String].?,
    "state".as[String]
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
                  generateAccessToken(owner, audience) { accessToken =>
                    val scope = apiAudienceToScope(audience)

                    val response = AccessTokenResponse(
                      access_token = accessToken.token,
                      token_type = TokenType.Bearer,
                      expires_in = accessToken.expiration,
                      state = request.state,
                      scope = scope
                    )

                    log.debugN("Successfully generated access token for client [{}]", client.id)

                    if (noRedirect) {
                      discardEntity & complete(
                        StatusCodes.OK,
                        AccessTokenResponseWithRedirectUri(
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
                "Encountered mismatched redirect URIs (expected [{}], found [{}])",
                client.redirectUri,
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
}

object ImplicitGrant {
  import play.api.libs.json.{Format, Json}

  def apply(
    config: Config,
    providers: Providers
  )(implicit system: ActorSystem): ImplicitGrant =
    new ImplicitGrant(
      config = config,
      providers = providers
    )

  implicit val accessTokenResponseWithRedirectUriFormat: Format[AccessTokenResponseWithRedirectUri] =
    Json.format[AccessTokenResponseWithRedirectUri]

  final case class AuthorizationRequest(
    response_type: ResponseType,
    client_id: Client.Id,
    redirect_uri: Option[String],
    scope: Option[String],
    state: String
  ) {
    require(response_type == ResponseType.Token, "response type must be 'token'")
    require(redirect_uri.forall(_.nonEmpty), "redirect URI must not be empty")
    require(scope.forall(_.nonEmpty), "scope must not be empty")
    require(state.nonEmpty, "state must not be empty")
  }

  final case class AccessTokenResponse(
    access_token: AccessToken,
    token_type: TokenType,
    expires_in: Seconds,
    state: String,
    scope: Option[String]
  ) {
    def asQuery: Uri.Query =
      Uri.Query(
        scope.foldLeft(
          Map(
            "access_token" -> access_token.value,
            "token_type" -> token_type.toString.toLowerCase,
            "expires_in" -> expires_in.value.toString,
            "state" -> state
          )
        ) { case (baseParams, actualScope) => baseParams + ("scope" -> actualScope) }
      )
  }

  final case class AccessTokenResponseWithRedirectUri(
    access_token: AccessToken,
    token_type: TokenType,
    expires_in: Seconds,
    state: String,
    scope: Option[String],
    redirect_uri: String
  )

  object AccessTokenResponseWithRedirectUri {
    def apply(
      response: AccessTokenResponse,
      responseRedirectUri: Uri
    ): AccessTokenResponseWithRedirectUri =
      AccessTokenResponseWithRedirectUri(
        access_token = response.access_token,
        token_type = response.token_type,
        expires_in = response.expires_in,
        state = response.state,
        scope = response.scope,
        redirect_uri = responseRedirectUri.toString
      )
  }

}
