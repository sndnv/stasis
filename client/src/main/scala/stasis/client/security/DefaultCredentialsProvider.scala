package stasis.client.security

import java.time.Instant

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.apache.pekko.actor.typed._
import org.apache.pekko.actor.typed.scaladsl.AskPattern._
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.actor.typed.scaladsl.TimerScheduler
import org.apache.pekko.http.scaladsl.model.headers.HttpCredentials
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken
import org.apache.pekko.util.Timeout
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import io.github.sndnv.layers.security.exceptions.ProviderFailure
import io.github.sndnv.layers.security.oauth.OAuthClient
import io.github.sndnv.layers.security.oauth.OAuthClient.AccessTokenResponse

class DefaultCredentialsProvider private (
  providerRef: ActorRef[DefaultCredentialsProvider.Message]
)(implicit system: ActorSystem[Nothing], timeout: Timeout)
    extends CredentialsProvider {
  import system.executionContext

  import DefaultCredentialsProvider._

  override def core: Future[HttpCredentials] =
    (providerRef ? (ref => GetCoreCredentials(ref))).flatMap(Future.fromTry)

  override def api: Future[HttpCredentials] =
    (providerRef ? (ref => GetApiCredentials(ref))).flatMap(Future.fromTry)
}

object DefaultCredentialsProvider {
  def apply(
    tokens: Tokens,
    client: OAuthClient
  )(implicit system: ActorSystem[Nothing], timeout: Timeout): DefaultCredentialsProvider = {
    implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

    val behaviour = Behaviors.withTimers[Message] { timers =>
      implicit val jwtClient: OAuthClient = client
      implicit val pekkoTimers: TimerScheduler[Message] = timers

      log.debugN(
        "Core token with scope [{}] received; expires in [{}] second(s)",
        tokens.core.scope.getOrElse("none"),
        tokens.core.expires_in.seconds
      )

      log.debugN(
        "API token with scope [{}] received; expires in [{}] second(s)",
        tokens.api.scope.getOrElse("none"),
        tokens.api.expires_in.seconds
      )

      provider(tokens = tokens)
    }

    new DefaultCredentialsProvider(
      providerRef = system.systemActorOf(
        behavior = behaviour,
        name = s"credentials-provider-${java.util.UUID.randomUUID().toString}"
      )
    )
  }

  final case class Tokens(
    core: AccessTokenResponse,
    coreExpiresAt: Instant,
    api: AccessTokenResponse,
    apiExpiresAt: Instant,
    expirationTolerance: FiniteDuration
  ) {
    def withCore(token: AccessTokenResponse): Tokens =
      copy(core = token, coreExpiresAt = Instant.now().plusSeconds(token.expires_in))

    def withApi(token: AccessTokenResponse): Tokens =
      copy(api = token, apiExpiresAt = Instant.now().plusSeconds(token.expires_in))

    def coreIsValid(): Boolean =
      coreExpiresAt.isAfter(Instant.now().plusMillis(expirationTolerance.toMillis))

    def apiIsValid(): Boolean =
      apiExpiresAt.isAfter(Instant.now().plusMillis(expirationTolerance.toMillis))
  }

  object Tokens {
    def apply(
      core: AccessTokenResponse,
      api: AccessTokenResponse,
      expirationTolerance: FiniteDuration
    ): Tokens = {
      val now = Instant.now()

      Tokens(
        core = core,
        coreExpiresAt = now.plusSeconds(core.expires_in),
        api = api,
        apiExpiresAt = now.plusSeconds(api.expires_in),
        expirationTolerance = expirationTolerance
      )
    }
  }

  private sealed trait Message
  private final case class GetCoreCredentials(replyTo: ActorRef[Try[HttpCredentials]]) extends Message
  private final case class GetApiCredentials(replyTo: ActorRef[Try[HttpCredentials]]) extends Message
  private final case class UpdateCoreToken(token: AccessTokenResponse) extends Message
  private final case class UpdateApiToken(token: AccessTokenResponse) extends Message
  private final case class RefreshCoreToken(replyTo: Option[ActorRef[Try[HttpCredentials]]]) extends Message
  private final case class RefreshApiToken(replyTo: Option[ActorRef[Try[HttpCredentials]]]) extends Message

  private case object RefreshCoreTokenKey
  private case object RefreshApiTokenKey

  private def provider(
    tokens: Tokens
  )(implicit timers: TimerScheduler[Message], client: OAuthClient, log: Logger): Behavior[Message] = {
    Behaviors.receive {
      case (_, GetCoreCredentials(replyTo)) if tokens.coreIsValid() =>
        replyTo ! Success(OAuth2BearerToken(token = tokens.core.access_token))
        Behaviors.same

      case (ctx, GetCoreCredentials(replyTo)) =>
        ctx.self ! RefreshCoreToken(replyTo = Some(replyTo))
        Behaviors.same

      case (_, GetApiCredentials(replyTo)) if tokens.apiIsValid() =>
        replyTo ! Success(OAuth2BearerToken(token = tokens.api.access_token))
        Behaviors.same

      case (ctx, GetApiCredentials(replyTo)) =>
        ctx.self ! RefreshApiToken(replyTo = Some(replyTo))
        Behaviors.same

      case (_, UpdateCoreToken(token)) =>
        val tokenExpiration = token.expires_in.seconds

        log.debugN(
          "Core token with scope [{}] updated; expires in [{}] second(s)",
          token.scope.getOrElse("none"),
          tokenExpiration
        )

        timers.startSingleTimer(
          key = RefreshCoreTokenKey,
          msg = RefreshCoreToken(replyTo = None),
          delay = tokenExpiration - tokens.expirationTolerance
        )

        provider(tokens = tokens.withCore(token))

      case (_, UpdateApiToken(token)) =>
        val tokenExpiration = token.expires_in.seconds

        log.debugN(
          "API token with scope [{}] updated; expires in [{}] second(s)",
          token.scope.getOrElse("none"),
          tokenExpiration
        )

        timers.startSingleTimer(
          key = RefreshApiTokenKey,
          msg = RefreshApiToken(replyTo = None),
          delay = tokenExpiration - tokens.expirationTolerance
        )

        provider(tokens = tokens.withApi(token))

      case (ctx, RefreshCoreToken(replyTo)) =>
        import ctx.executionContext

        val self = ctx.self

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
                  replyTo.foreach(_ ! Success(OAuth2BearerToken(token = token.access_token)))

                case Failure(e) =>
                  log.errorN(
                    "Failed to refresh core token from [{}]: [{} - {}]",
                    client.tokenEndpoint,
                    e.getClass.getSimpleName,
                    e.getMessage
                  )
                  replyTo.foreach(_ ! Failure(e))
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
                  replyTo.foreach(_ ! Success(OAuth2BearerToken(token = token.access_token)))

                case Failure(e) =>
                  log.errorN(
                    "Failed to retrieve new core token from [{}]: [{} - {}]",
                    client.tokenEndpoint,
                    e.getClass.getSimpleName,
                    e.getMessage
                  )
                  replyTo.foreach(_ ! Failure(e))
              }
        }

        Behaviors.same

      case (ctx, RefreshApiToken(replyTo)) =>
        import ctx.executionContext

        val self = ctx.self

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
                  replyTo.foreach(_ ! Success(OAuth2BearerToken(token = token.access_token)))

                case Failure(e) =>
                  log.errorN(
                    "Failed to refresh API token from [{}]: [{} - {}]",
                    client.tokenEndpoint,
                    e.getClass.getSimpleName,
                    e.getMessage
                  )
                  replyTo.foreach(_ ! Failure(e))
              }

          case None =>
            val message = s"Cannot refresh API token from [${client.tokenEndpoint}]; refresh token is not available"
            log.errorN(message)
            replyTo.foreach(_ ! Failure(ProviderFailure(message)))
        }

        Behaviors.same
    }
  }
}
