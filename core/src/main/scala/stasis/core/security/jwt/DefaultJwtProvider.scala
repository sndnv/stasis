package stasis.core.security.jwt

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.util.Timeout
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.core.security.oauth.OAuthClient
import stasis.core.security.oauth.OAuthClient.AccessTokenResponse

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class DefaultJwtProvider(
  client: OAuthClient,
  expirationTolerance: FiniteDuration
)(implicit system: ActorSystem[SpawnProtocol.Command], timeout: Timeout)
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
            parameters = OAuthClient.GrantParameters.ClientCredentials()
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
