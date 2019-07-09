package stasis.identity.api.oauth

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import stasis.identity.api.Formats._
import stasis.identity.api.oauth.directives.AuthDirectives
import stasis.identity.api.oauth.setup.Providers
import stasis.identity.model.clients.Client
import stasis.identity.model.realms.Realm
import stasis.identity.model.tokens.{AccessToken, TokenType}
import stasis.identity.model.{ResponseType, Seconds}

import scala.concurrent.ExecutionContext

class ImplicitGrant(
  override val providers: Providers
)(implicit system: ActorSystem, override val mat: Materializer)
    extends AuthDirectives {
  import ImplicitGrant._

  override implicit protected def ec: ExecutionContext = system.dispatcher
  override protected def log: LoggingAdapter = Logging(system, this.getClass.getName)

  def authorization(realm: Realm): Route =
    get {
      parameters(
        "response_type".as[ResponseType],
        "client_id".as[Client.Id],
        "redirect_uri".as[String].?,
        "scope".as[String].?,
        "state".as[String]
      ).as(AuthorizationRequest) { request =>
        retrieveClient(request.client_id) {
          case client if request.redirect_uri.forall(_ == client.redirectUri) =>
            val redirectUri = Uri(request.redirect_uri.getOrElse(client.redirectUri))

            (authenticateResourceOwner(redirectUri, request.state) & extractApiAudience(realm.id, request.scope)) {
              (owner, audience) =>
                generateAccessToken(owner, audience) { accessToken =>
                  val scope = apiAudienceToScope(audience)

                  val response = AccessTokenResponse(
                    access_token = accessToken,
                    token_type = TokenType.Bearer,
                    expires_in = client.tokenExpiration,
                    state = request.state,
                    scope = scope
                  )

                  log.debug(
                    "Realm [{}]: Successfully generated access token for client [{}]",
                    realm.id,
                    client.id
                  )

                  redirect(
                    redirectUri.withQuery(response.asQuery),
                    StatusCodes.Found
                  )
                }
            }

          case client =>
            log.warning(
              "Realm [{}]: Encountered mismatched redirect URIs (expected [{}], found [{}])",
              realm.id,
              client.redirectUri,
              request.redirect_uri
            )

            complete(
              StatusCodes.BadRequest,
              "The request has missing, invalid or mismatching redirection URI and/or client identifier"
            )
        }
      }
    }
}

object ImplicitGrant {
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
}
