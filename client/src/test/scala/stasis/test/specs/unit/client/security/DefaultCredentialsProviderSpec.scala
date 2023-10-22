package stasis.test.specs.unit.client.security

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken
import org.scalatest.Assertion
import org.scalatest.concurrent.Eventually
import stasis.client.security.DefaultCredentialsProvider
import stasis.core.security.oauth.OAuthClient.AccessTokenResponse
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.security.mocks.MockOAuthClient

import scala.concurrent.duration._

class DefaultCredentialsProviderSpec extends AsyncUnitSpec with Eventually {
  "A DefaultCredentialsProvider" should "provide core credentials" in {
    val (provider, client) = createProvider()

    provider.core
      .map {
        case OAuth2BearerToken(actualToken) =>
          actualToken should be(defaultCoreToken.access_token)
          client.tokensProvided should be(0)

        case other =>
          fail(s"Unexpected token received: [$other]")
      }
  }

  it should "provide API credentials" in {
    val (provider, client) = createProvider()

    provider.api
      .map {
        case OAuth2BearerToken(actualToken) =>
          actualToken should be(defaultApiToken.access_token)
          client.tokensProvided should be(0)

        case other =>
          fail(s"Unexpected token received: [$other]")
      }
  }

  it should "support refreshing core tokens" in {
    val (provider, client) = createProvider(
      initialCoreToken = defaultCoreToken.copy(expires_in = 1),
      expirationTolerance = 1.second
    )

    eventually[Assertion] {
      val token = provider.core.await

      token match {
        case OAuth2BearerToken(actualToken) =>
          actualToken should be(defaultNewToken.access_token)
          client.clientCredentialsTokensProvided should be(0)
          client.passwordTokensProvided should be(0)
          client.refreshTokensProvided should be(1)

        case other =>
          fail(s"Unexpected token received: [$other]")
      }
    }
  }

  it should "support refreshing API tokens" in {
    val (provider, client) = createProvider(
      initialApiToken = defaultApiToken.copy(expires_in = 1),
      expirationTolerance = 1.second
    )

    eventually[Assertion] {
      val token = provider.api.await

      token match {
        case OAuth2BearerToken(actualToken) =>
          actualToken should be(defaultNewToken.access_token)
          client.clientCredentialsTokensProvided should be(0)
          client.passwordTokensProvided should be(0)
          client.refreshTokensProvided should be(1)

        case other =>
          fail(s"Unexpected token received: [$other]")
      }
    }
  }

  it should "support retrieving new core tokens if a refresh token is not available" in {
    val (provider, client) = createProvider(
      initialCoreToken = defaultCoreToken.copy(expires_in = 1, refresh_token = None),
      expirationTolerance = 1.second
    )

    eventually[Assertion] {
      val token = provider.core.await

      token match {
        case OAuth2BearerToken(actualToken) =>
          actualToken should be(defaultNewToken.access_token)
          client.clientCredentialsTokensProvided should be(1)
          client.passwordTokensProvided should be(0)
          client.refreshTokensProvided should be(0)

        case other =>
          fail(s"Unexpected token received: [$other]")
      }
    }
  }

  it should "fail if not API refresh token is available" in {
    val (provider, client) = createProvider(
      initialApiToken = defaultApiToken.copy(expires_in = 1, refresh_token = None),
      expirationTolerance = 1.second
    )

    eventually[Assertion] {
      val token = provider.api.await

      token match {
        case OAuth2BearerToken(actualToken) =>
          actualToken should be(defaultApiToken.access_token)
          client.tokensProvided should be(0)

        case other =>
          fail(s"Unexpected token received: [$other]")
      }
    }
  }

  private def createProvider(
    initialCoreToken: AccessTokenResponse = defaultCoreToken,
    initialApiToken: AccessTokenResponse = defaultApiToken,
    newToken: AccessTokenResponse = defaultNewToken,
    expirationTolerance: FiniteDuration = 3.seconds
  ): (DefaultCredentialsProvider, MockOAuthClient) = {
    val client = new MockOAuthClient(token = Some(newToken))

    val provider = DefaultCredentialsProvider(
      tokens = DefaultCredentialsProvider.Tokens(
        core = initialCoreToken,
        api = initialApiToken,
        expirationTolerance = expirationTolerance
      ),
      client = client
    )

    (provider, client)
  }

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 250.milliseconds)

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "DefaultCredentialsProviderSpec"
  )

  private val defaultNewToken: AccessTokenResponse = AccessTokenResponse(
    access_token = "test-new-access-token",
    refresh_token = Some("test-new-refresh-token"),
    expires_in = 42,
    scope = Some("test-scope")
  )

  private val defaultCoreToken: AccessTokenResponse =
    defaultNewToken.copy(access_token = "test-core-access-token")

  private val defaultApiToken: AccessTokenResponse =
    defaultNewToken.copy(access_token = "test-api-access-token")
}
