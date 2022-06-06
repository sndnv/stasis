package stasis.core.security.jwt

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.util.Timeout
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.core.security.oauth.OAuthClient
import stasis.core.security.oauth.OAuthClient.AccessTokenResponse
import stasis.core.telemetry.TelemetryContext

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class DefaultJwtProvider(
  client: OAuthClient,
  clientParameters: OAuthClient.GrantParameters,
  expirationTolerance: FiniteDuration
)(implicit system: ActorSystem[SpawnProtocol.Command], telemetry: TelemetryContext, timeout: Timeout)
    extends JwtProvider {
  private val untypedSystem = system.classicSystem
  private implicit val ec: ExecutionContext = system.executionContext

  private val cache = MemoryBackend[String, AccessTokenResponse](name = "jwt-provider-cache")

  override def provide(scope: String): Future[String] =
    cache.get(scope).flatMap {
      case Some(response) =>
        Future.successful(response.access_token)

      case None =>
        for {
          response <- client.token(
            scope = Some(scope),
            parameters = clientParameters
          )
          _ <- cache.put(scope, response)
        } yield {
          val _ = akka.pattern.after(
            duration = response.expires_in.seconds - expirationTolerance,
            using = untypedSystem.scheduler
          )(cache.delete(scope))

          response.access_token
        }
    }
}

object DefaultJwtProvider {
  def apply(
    client: OAuthClient,
    expirationTolerance: FiniteDuration
  )(implicit system: ActorSystem[SpawnProtocol.Command], telemetry: TelemetryContext, timeout: Timeout): DefaultJwtProvider =
    new DefaultJwtProvider(
      client = client,
      clientParameters = OAuthClient.GrantParameters.ClientCredentials(),
      expirationTolerance = expirationTolerance
    )

  def apply(
    client: OAuthClient,
    clientParameters: OAuthClient.GrantParameters,
    expirationTolerance: FiniteDuration
  )(implicit system: ActorSystem[SpawnProtocol.Command], telemetry: TelemetryContext, timeout: Timeout): DefaultJwtProvider =
    new DefaultJwtProvider(
      client = client,
      clientParameters = clientParameters,
      expirationTolerance = expirationTolerance
    )
}
