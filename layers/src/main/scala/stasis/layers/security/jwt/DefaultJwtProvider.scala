package stasis.layers.security.jwt

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import stasis.layers.persistence.memory.MemoryStore
import stasis.layers.security.oauth.OAuthClient
import stasis.layers.security.oauth.OAuthClient.AccessTokenResponse
import stasis.layers.telemetry.TelemetryContext

class DefaultJwtProvider(
  client: OAuthClient,
  clientParameters: OAuthClient.GrantParameters,
  expirationTolerance: FiniteDuration
)(implicit system: ActorSystem[Nothing], telemetry: TelemetryContext, timeout: Timeout)
    extends JwtProvider {
  private val untypedSystem = system.classicSystem
  private implicit val ec: ExecutionContext = system.executionContext

  private val cache = MemoryStore[String, AccessTokenResponse](name = "jwt-provider-cache")

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
          val _ = org.apache.pekko.pattern.after(
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
  )(implicit system: ActorSystem[Nothing], telemetry: TelemetryContext, timeout: Timeout): DefaultJwtProvider =
    new DefaultJwtProvider(
      client = client,
      clientParameters = OAuthClient.GrantParameters.ClientCredentials(),
      expirationTolerance = expirationTolerance
    )

  def apply(
    client: OAuthClient,
    clientParameters: OAuthClient.GrantParameters,
    expirationTolerance: FiniteDuration
  )(implicit system: ActorSystem[Nothing], telemetry: TelemetryContext, timeout: Timeout): DefaultJwtProvider =
    new DefaultJwtProvider(
      client = client,
      clientParameters = clientParameters,
      expirationTolerance = expirationTolerance
    )
}
