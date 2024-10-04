package stasis.client.security

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

import org.apache.pekko.actor.typed._
import org.apache.pekko.actor.typed.scaladsl.AskPattern._
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.actor.typed.scaladsl.TimerScheduler
import org.apache.pekko.http.scaladsl.model.headers.HttpCredentials
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken
import org.apache.pekko.util.Timeout

import stasis.layers.security.oauth.OAuthClient
import stasis.layers.security.oauth.OAuthClient.AccessTokenResponse

class DefaultCredentialsProvider private (
  providerRef: ActorRef[DefaultCredentialsProvider.Message]
)(implicit scheduler: Scheduler, timeout: Timeout)
    extends CredentialsProvider {
  import DefaultCredentialsProvider._

  override def core: Future[HttpCredentials] = providerRef ? (ref => GetCoreCredentials(ref))
  override def api: Future[HttpCredentials] = providerRef ? (ref => GetApiCredentials(ref))
}

object DefaultCredentialsProvider {
  def apply(
    tokens: Tokens,
    client: OAuthClient
  )(implicit system: ActorSystem[Nothing], timeout: Timeout): DefaultCredentialsProvider = {
    val behaviour = Behaviors.setup[Message] { ctx =>
      Behaviors.withTimers[Message] { timers =>
        implicit val jwtClient: OAuthClient = client
        implicit val pekkoTimers: TimerScheduler[Message] = timers

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

        import ctx.executionContext
        provider(tokens = tokens)
      }
    }

    new DefaultCredentialsProvider(
      providerRef = system.systemActorOf(behaviour, name = s"credentials-provider-${java.util.UUID.randomUUID().toString}")
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
      case (_, GetCoreCredentials(replyTo)) =>
        replyTo ! OAuth2BearerToken(token = tokens.core.access_token)
        Behaviors.same

      case (_, GetApiCredentials(replyTo)) =>
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
            log.debugN("Refreshing core token from [{}]...", client.tokenEndpoint)

            client
              .token(
                scope = tokens.core.scope,
                parameters = OAuthClient.GrantParameters.RefreshToken(token)
              )
              .onComplete {
                case Success(token) =>
                  self ! UpdateCoreToken(token)

                case Failure(e) =>
                  log.errorN(
                    "Failed to refresh core token from [{}]: [{} - {}]",
                    client.tokenEndpoint,
                    e.getClass.getSimpleName,
                    e.getMessage
                  )
              }

          case None =>
            log.debugN("Retrieving new core token from [{}]...", client.tokenEndpoint)

            client
              .token(
                scope = tokens.core.scope,
                parameters = OAuthClient.GrantParameters.ClientCredentials()
              )
              .onComplete {
                case Success(token) =>
                  self ! UpdateCoreToken(token)

                case Failure(e) =>
                  log.errorN(
                    "Failed to retrieve new core token from [{}]: [{} - {}]",
                    client.tokenEndpoint,
                    e.getClass.getSimpleName,
                    e.getMessage
                  )
              }
        }

        Behaviors.same

      case (ctx, RefreshApiToken) =>
        val self = ctx.self
        val log = ctx.log

        tokens.api.refresh_token match {
          case Some(token) =>
            log.debugN("Refreshing API token from [{}]...", client.tokenEndpoint)
            client
              .token(
                scope = tokens.api.scope,
                parameters = OAuthClient.GrantParameters.RefreshToken(token)
              )
              .onComplete {
                case Success(token) =>
                  self ! UpdateApiToken(token)

                case Failure(e) =>
                  log.errorN(
                    "Failed to refresh API token from [{}]: [{} - {}]",
                    client.tokenEndpoint,
                    e.getClass.getSimpleName,
                    e.getMessage
                  )
              }

          case None =>
            log.errorN(
              "Cannot refresh API token from [{}]; refresh token is not available",
              client.tokenEndpoint
            )
        }

        Behaviors.same
    }
}
