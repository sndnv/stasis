package stasis.client.security

import akka.actor.Scheduler
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SpawnProtocol}
import akka.http.scaladsl.model.headers.{HttpCredentials, OAuth2BearerToken}
import akka.util.Timeout
import stasis.core.security.oauth.OAuthClient
import stasis.core.security.oauth.OAuthClient.AccessTokenResponse

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

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
  )(implicit system: ActorSystem[SpawnProtocol], timeout: Timeout): DefaultCredentialsProvider = {
    implicit val scheduler: Scheduler = system.scheduler
    implicit val ec: ExecutionContext = system.executionContext

    val behaviour = Behaviors.withTimers[Message] { timers =>
      implicit val jwtClient: OAuthClient = client
      implicit val akkaTimers: TimerScheduler[Message] = timers

      timers.startSingleTimer(
        key = RefreshCoreTokenKey,
        msg = RefreshCoreToken,
        delay = tokens.core.expires_in.seconds - tokens.expirationTolerance
      )

      timers.startSingleTimer(
        key = RefreshApiTokenKey,
        msg = RefreshApiToken,
        delay = tokens.api.expires_in.seconds - tokens.expirationTolerance
      )

      provider(tokens = tokens)
    }

    new DefaultCredentialsProvider(
      providerRef = system ? SpawnProtocol.Spawn(behaviour, name = "credentials-provider")
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
        ctx.log.debug("Responding with core token with scope [{}]", tokens.core.scope)
        replyTo ! OAuth2BearerToken(token = tokens.core.access_token)
        Behaviors.same

      case (ctx, GetApiCredentials(replyTo)) =>
        ctx.log.debug("Responding with API token with scope [{}]", tokens.api.scope)
        replyTo ! OAuth2BearerToken(token = tokens.api.access_token)
        Behaviors.same

      case (ctx, UpdateCoreToken(token)) =>
        val tokenExpiration = token.expires_in.seconds

        ctx.log.debug(
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

        ctx.log.debug(
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
            log.debug("Refreshing core token...")
            client
              .token(
                scope = tokens.core.scope,
                parameters = OAuthClient.GrantParameters.RefreshToken(token)
              )
              .map(self ! UpdateCoreToken(_))
              .recover {
                case NonFatal(e) =>
                  log.error(e, "Failed to refresh core token: [{}]", e.getMessage)
              }

          case None =>
            log.debug("Retrieving new core token...")
            client
              .token(
                scope = tokens.core.scope,
                parameters = OAuthClient.GrantParameters.ClientCredentials()
              )
              .map(self ! UpdateCoreToken(_))
              .recover {
                case NonFatal(e) =>
                  log.error(e, "Failed to retrieve new core token: [{}]", e.getMessage)
              }
        }

        Behaviors.same

      case (ctx, RefreshApiToken) =>
        val self = ctx.self
        val log = ctx.log

        tokens.api.refresh_token match {
          case Some(token) =>
            log.debug("Refreshing API token...")
            client
              .token(
                scope = tokens.api.scope,
                parameters = OAuthClient.GrantParameters.RefreshToken(token)
              )
              .map(self ! UpdateApiToken(_))
              .recover {
                case NonFatal(e) =>
                  log.error(e, "Failed to refresh API token: [{}]", e.getMessage)
              }

          case None =>
            log.error("Cannot refresh API token; refresh token is not available")
        }

        Behaviors.same
    }
}
