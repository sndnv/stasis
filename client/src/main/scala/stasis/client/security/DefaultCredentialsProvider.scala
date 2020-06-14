package stasis.client.security

import akka.actor.typed._
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.{Behaviors, LoggerOps, TimerScheduler}
import akka.http.scaladsl.model.headers.{HttpCredentials, OAuth2BearerToken}
import akka.util.Timeout
import stasis.core.security.oauth.OAuthClient
import stasis.core.security.oauth.OAuthClient.AccessTokenResponse

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class DefaultCredentialsProvider private (
  providerRef: Future[ActorRef[DefaultCredentialsProvider.Message]]
)(implicit scheduler: Scheduler, ec: ExecutionContext, timeout: Timeout)
    extends CredentialsProvider {
  import DefaultCredentialsProvider._

  override def core: Future[HttpCredentials] = providerRef.flatMap(_ ? (ref => GetCoreCredentials(ref)))
  override def api: Future[HttpCredentials] = providerRef.flatMap(_ ? (ref => GetApiCredentials(ref)))
}

object DefaultCredentialsProvider {
  def apply(
    tokens: Tokens,
    client: OAuthClient
  )(implicit system: ActorSystem[SpawnProtocol.Command], timeout: Timeout): DefaultCredentialsProvider = {
    implicit val ec: ExecutionContext = system.executionContext

    val behaviour = Behaviors.setup[Message] { ctx =>
      Behaviors.withTimers[Message] { timers =>
        implicit val jwtClient: OAuthClient = client
        implicit val akkaTimers: TimerScheduler[Message] = timers

        val coreTokenExpiration = tokens.core.expires_in.seconds
        val apiTokenExpiration = tokens.api.expires_in.seconds

        ctx.log.debugN(
          "Core token with scope [{}] received; expires in [{}] second(s)",
          tokens.core.scope.getOrElse("none"),
          coreTokenExpiration
        )

        timers.startSingleTimer(
          key = RefreshCoreTokenKey,
          msg = RefreshCoreToken,
          delay = tokens.core.expires_in.seconds - tokens.expirationTolerance
        )

        ctx.log.debugN(
          "API token with scope [{}] received; expires in [{}] second(s)",
          tokens.api.scope.getOrElse("none"),
          apiTokenExpiration
        )

        timers.startSingleTimer(
          key = RefreshApiTokenKey,
          msg = RefreshApiToken,
          delay = tokens.api.expires_in.seconds - tokens.expirationTolerance
        )

        provider(tokens = tokens)
      }
    }

    new DefaultCredentialsProvider(
      providerRef = system ? (SpawnProtocol.Spawn(behaviour, name = "credentials-provider", props = Props.empty, _))
    )
  }

  final case class Tokens(
    core: AccessTokenResponse,
    api: AccessTokenResponse,
    expirationTolerance: FiniteDuration
  ) {
    def withCore(token: AccessTokenResponse): Tokens = copy(core = token)
    def withApi(token: AccessTokenResponse): Tokens = copy(api = token)
  }

  private sealed trait Message
  private final case class GetCoreCredentials(replyTo: ActorRef[HttpCredentials]) extends Message
  private final case class GetApiCredentials(replyTo: ActorRef[HttpCredentials]) extends Message
  private final case class UpdateCoreToken(token: AccessTokenResponse) extends Message
  private final case class UpdateApiToken(token: AccessTokenResponse) extends Message
  private case object RefreshCoreToken extends Message
  private case object RefreshApiToken extends Message

  private case object RefreshCoreTokenKey
  private case object RefreshApiTokenKey

  private def provider(
    tokens: Tokens
  )(implicit timers: TimerScheduler[Message], client: OAuthClient, ec: ExecutionContext): Behavior[Message] =
    Behaviors.receive {
      case (ctx, GetCoreCredentials(replyTo)) =>
        ctx.log.debugN("Responding with core token with scope [{}]", tokens.core.scope)
        replyTo ! OAuth2BearerToken(token = tokens.core.access_token)
        Behaviors.same

      case (ctx, GetApiCredentials(replyTo)) =>
        ctx.log.debugN("Responding with API token with scope [{}]", tokens.api.scope)
        replyTo ! OAuth2BearerToken(token = tokens.api.access_token)
        Behaviors.same

      case (ctx, UpdateCoreToken(token)) =>
        val tokenExpiration = token.expires_in.seconds

        ctx.log.debugN(
          "Core token with scope [{}] updated; expires in [{}] second(s)",
          token.scope.getOrElse("none"),
          tokenExpiration
        )

        timers.startSingleTimer(
          key = RefreshCoreTokenKey,
          msg = RefreshCoreToken,
          delay = tokenExpiration - tokens.expirationTolerance
        )

        provider(tokens = tokens.withCore(token))

      case (ctx, UpdateApiToken(token)) =>
        val tokenExpiration = token.expires_in.seconds

        ctx.log.debugN(
          "API token with scope [{}] updated; expires in [{}] second(s)",
          token.scope.getOrElse("none"),
          tokenExpiration
        )

        timers.startSingleTimer(
          key = RefreshApiTokenKey,
          msg = RefreshApiToken,
          delay = tokenExpiration - tokens.expirationTolerance
        )

        provider(tokens = tokens.withApi(token))

      case (ctx, RefreshCoreToken) =>
        val self = ctx.self
        val log = ctx.log

        tokens.core.refresh_token match {
          case Some(token) =>
            log.debugN("Refreshing core token...")
            client
              .token(
                scope = tokens.core.scope,
                parameters = OAuthClient.GrantParameters.RefreshToken(token)
              )
              .onComplete {
                case Success(token) => self ! UpdateCoreToken(token)
                case Failure(e)     => log.errorN("Failed to refresh core token: [{}]", e.getMessage, e)
              }

          case None =>
            log.debugN("Retrieving new core token...")
            client
              .token(
                scope = tokens.core.scope,
                parameters = OAuthClient.GrantParameters.ClientCredentials()
              )
              .onComplete {
                case Success(token) => self ! UpdateCoreToken(token)
                case Failure(e)     => log.errorN("Failed to retrieve new core token: [{}]", e.getMessage, e)
              }
        }

        Behaviors.same

      case (ctx, RefreshApiToken) =>
        val self = ctx.self
        val log = ctx.log

        tokens.api.refresh_token match {
          case Some(token) =>
            log.debugN("Refreshing API token...")
            client
              .token(
                scope = tokens.api.scope,
                parameters = OAuthClient.GrantParameters.RefreshToken(token)
              )
              .onComplete {
                case Success(token) => self ! UpdateApiToken(token)
                case Failure(e)     => log.errorN("Failed to refresh API token: [{}]", e.getMessage, e)
              }

          case None =>
            log.errorN("Cannot refresh API token; refresh token is not available")
        }

        Behaviors.same
    }
}
