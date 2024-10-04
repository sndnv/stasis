package stasis.layers.security.jwt

import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.scalatest.BeforeAndAfterAll

import stasis.layers.UnitSpec
import stasis.layers.security.jwt
import stasis.layers.security.mocks.MockOAuthClient
import stasis.layers.security.oauth.OAuthClient
import stasis.layers.telemetry.MockTelemetryContext

class DefaultJwtProviderSpec extends UnitSpec with BeforeAndAfterAll {
  "A DefaultJwtProvider" should "successfully provide cached tokens" in {
    val mockClient = new MockOAuthClient(token = Some(token))
    val provider = DefaultJwtProvider(client = mockClient, expirationTolerance = 3.seconds)

    for {
      response1 <- provider.provide(scope = client)
      response2 <- provider.provide(scope = client)
      response3 <- provider.provide(scope = client)
    } yield {
      mockClient.tokensProvided should be(1)

      response1 should not be empty
      response2 should not be empty
      response3 should not be empty
    }
  }

  it should "expire cached tokens" in {
    val expiration = 1.second
    val tolerance = 800.millis
    val waitDelay = ((expiration - tolerance) * 1.5).toMillis.millis

    val mockClient = new MockOAuthClient(token = Some(token.copy(expires_in = expiration.toSeconds)))
    val provider = jwt.DefaultJwtProvider(client = mockClient, expirationTolerance = 800.millis)

    for {
      response1 <- provider.provide(scope = client)
      response2 <- provider.provide(scope = client)
      response3 <- provider.provide(scope = client)
      _ <- after(waitDelay, system)(Future.successful(Done))
      response4 <- provider.provide(scope = client)
      response5 <- provider.provide(scope = client)
    } yield {
      mockClient.tokensProvided should be(2)

      response1 should not be empty
      response2 should not be empty
      response3 should not be empty
      response4 should not be empty
      response5 should not be empty
    }
  }

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "DefaultJwtProviderSpec"
  )

  private implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

  private val client = "some-client"

  private val token = OAuthClient.AccessTokenResponse(
    access_token = "some-token",
    refresh_token = None,
    expires_in = 42,
    scope = Some(client)
  )

  override protected def afterAll(): Unit =
    system.terminate()
}
