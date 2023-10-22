package stasis.test.specs.unit.core.security.oauth

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.BeforeAndAfterAll
import stasis.core.security.oauth.DefaultOAuthClient
import stasis.core.security.oauth.OAuthClient.GrantParameters
import stasis.core.security.tls.EndpointContext
import stasis.core.telemetry.TelemetryContext
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.security.mocks.{MockJwksGenerators, MockJwtEndpoint}
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

import scala.collection.mutable
import scala.util.control.NonFatal

class DefaultOAuthClientSpec extends AsyncUnitSpec with BeforeAndAfterAll {
  "A DefaultOAuthClient" should "successfully retrieve tokens (client credentials)" in {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val expiration = 42
    val endpoint = createEndpoint(port = ports.dequeue(), expirationSeconds = expiration)
    endpoint.start()

    val client = createClient(endpoint = s"${endpoint.url}/token")

    client
      .token(
        scope = Some(clientId),
        parameters = GrantParameters.ClientCredentials()
      )
      .map { response =>
        endpoint.stop()

        endpoint.count("/token") should be(1)

        response.access_token should not be empty
        response.expires_in should be(expiration)
        response.scope should be(Some(clientId))

        telemetry.security.oauthClient.token should be(1)
      }
  }

  it should "successfully retrieve tokens (resource owner password credentials)" in {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val expiration = 42
    val endpoint = createEndpoint(port = ports.dequeue(), expirationSeconds = expiration)
    endpoint.start()

    val client = createClient(endpoint = s"${endpoint.url}/token")

    client
      .token(
        scope = Some(clientId),
        parameters = GrantParameters.ResourceOwnerPasswordCredentials(username = user, password = userPassword)
      )
      .map { response =>
        endpoint.stop()

        endpoint.count("/token") should be(1)

        response.access_token should not be empty
        response.expires_in should be(expiration)
        response.scope should be(Some(clientId))

        telemetry.security.oauthClient.token should be(1)
      }
  }

  it should "successfully retrieve tokens (refresh)" in {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val expiration = 42
    val endpoint = createEndpoint(port = ports.dequeue(), expirationSeconds = expiration)
    endpoint.start()

    val client = createClient(endpoint = s"${endpoint.url}/token")

    client
      .token(
        scope = Some(clientId),
        parameters = GrantParameters.RefreshToken(refreshToken = refreshToken)
      )
      .map { response =>
        endpoint.stop()

        endpoint.count("/token") should be(1)

        response.access_token should not be empty
        response.expires_in should be(expiration)
        response.scope should be(Some(clientId))

        telemetry.security.oauthClient.token should be(1)
      }
  }

  it should "support providing no scope" in {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val expiration = 42
    val endpoint = createEndpoint(port = ports.dequeue(), expirationSeconds = expiration)
    endpoint.start()

    val client = createClient(endpoint = s"${endpoint.url}/token")

    client
      .token(
        scope = None,
        parameters = GrantParameters.ClientCredentials()
      )
      .map { response =>
        endpoint.stop()

        endpoint.count("/token") should be(1)

        response.access_token should not be empty
        response.expires_in should be(expiration)
        response.scope should be(None)

        telemetry.security.oauthClient.token should be(1)
      }
  }

  it should "support providing grant parameters as form parameters" in {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val expiration = 42
    val endpoint = createEndpoint(port = ports.dequeue(), expirationSeconds = expiration)
    endpoint.start()

    val client = createClient(endpoint = s"${endpoint.url}/token", useQueryString = false)

    client
      .token(
        scope = Some(clientId),
        parameters = GrantParameters.ClientCredentials()
      )
      .map { response =>
        endpoint.stop()

        endpoint.count("/token") should be(1)

        response.access_token should not be empty
        response.expires_in should be(expiration)
        response.scope should be(Some(clientId))

        telemetry.security.oauthClient.token should be(1)
      }
  }

  it should "handle token request failures" in {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val endpoint = "http://localhost/token"
    val client = createClient(endpoint)

    client
      .token(
        scope = Some(clientId),
        parameters = GrantParameters.ClientCredentials()
      )
      .map { response =>
        fail(s"Unexpected response received: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should startWith("Failed to retrieve token")
        e.getMessage should include("Connection refused")
        telemetry.security.oauthClient.token should be(0)
      }
  }

  it should "handle failures during token unmarshalling" in {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val endpoint = createEndpoint(port = ports.dequeue())
    endpoint.start()

    val client = createClient(endpoint = s"${endpoint.url}/token/invalid")

    client
      .token(
        scope = Some(clientId),
        parameters = GrantParameters.ClientCredentials()
      )
      .map { response =>
        fail(s"Unexpected response received: [$response]")
      }
      .recover { case NonFatal(e) =>
        endpoint.stop()
        e.getMessage should startWith("Failed to unmarshal response [200 OK]")
        e.getMessage should include("Unsupported Content-Type")
        telemetry.security.oauthClient.token should be(0)
      }
  }

  it should "handle unexpected token endpoint responses" in {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val endpoint = createEndpoint(port = ports.dequeue())
    endpoint.start()

    val tokenEndpoint = s"${endpoint.url}/token/error"
    val client = createClient(endpoint = tokenEndpoint)

    client
      .token(
        scope = Some(clientId),
        parameters = GrantParameters.ClientCredentials()
      )
      .map { response =>
        fail(s"Unexpected response received: [$response]")
      }
      .recover { case NonFatal(e) =>
        endpoint.stop()

        e.getMessage should startWith(
          s"Token retrieval from [$tokenEndpoint] failed with [500 Internal Server Error]"
        )

        telemetry.security.oauthClient.token should be(0)
      }
  }

  it should "support custom connection contexts" in {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val config: Config = ConfigFactory.load().getConfig("stasis.test.core.security.tls")

    val serverContextConfig = EndpointContext.Config(config.getConfig("context-server-jks"))

    val clientContext = EndpointContext(
      config = EndpointContext.Config(config.getConfig("context-client"))
    )

    val expiration = 42

    val endpoint = createEndpoint(
      port = ports.dequeue(),
      expirationSeconds = expiration,
      keystoreConfig = serverContextConfig.keyStoreConfig
    )

    endpoint.start()

    val client = createClient(endpoint = s"${endpoint.url}/token", context = Some(clientContext))

    client
      .token(
        scope = Some(clientId),
        parameters = GrantParameters.ClientCredentials()
      )
      .map { response =>
        endpoint.stop()

        endpoint.count("/token") should be(1)

        response.access_token should not be empty
        response.expires_in should be(expiration)
        response.scope should be(Some(clientId))

        telemetry.security.oauthClient.token should be(1)
      }
  }

  private def createClient(
    endpoint: String,
    useQueryString: Boolean = true,
    context: Option[EndpointContext] = None
  )(implicit telemetry: TelemetryContext): DefaultOAuthClient =
    new DefaultOAuthClient(
      tokenEndpoint = endpoint,
      client = clientId,
      clientSecret = clientSecret,
      useQueryString = useQueryString,
      context = context
    )

  private def createEndpoint(
    port: Int,
    expirationSeconds: Int = 6000,
    keystoreConfig: Option[EndpointContext.StoreConfig] = None
  ): MockJwtEndpoint =
    new MockJwtEndpoint(
      port = port,
      credentials = MockJwtEndpoint.ExpectedCredentials(
        subject = clientId,
        secret = clientSecret,
        refreshToken = refreshToken,
        user = user,
        userPassword = userPassword
      ),
      expirationSeconds = expirationSeconds.toLong,
      signatureKey = MockJwksGenerators.generateRandomRsaKey(keyId = Some("rsa-0")),
      withKeystoreConfig = keystoreConfig
    )

  private implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "OAuthClientSpec"
  )

  private val ports: mutable.Queue[Int] = (26000 to 26100).to(mutable.Queue)

  private val clientId: String = "some-client"
  private val clientSecret: String = "some-client-secret"
  private val refreshToken: String = "some-token"
  private val user: String = "some-user"
  private val userPassword = "some-password"

  override protected def afterAll(): Unit =
    system.terminate()
}
