package stasis.test.specs.unit.core.security.jwt

import akka.Done
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.http.scaladsl.HttpsConnectionContext
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.BeforeAndAfterAll
import stasis.core.security.jwt.JwtProvider
import stasis.core.security.tls.EndpointContext
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.security.mocks.{MockJwksGenerators, MockJwtEndpoint}

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

class JwtProviderSpec extends AsyncUnitSpec with BeforeAndAfterAll {
  "A JwtProvider" should "successfully retrieve tokens" in {
    val expiration = 42

    val endpoint = createEndpoint(port = ports.dequeue(), expirationSeconds = expiration)
    endpoint.start()

    val provider = createProvider(endpoint = s"${endpoint.url}/token")

    provider
      .request(scope = client)
      .map { response =>
        endpoint.stop()

        endpoint.count("/token") should be(1)

        response.access_token should not be empty
        response.expires_in should be(expiration)
        response.scope should be(Some(client))
      }
  }

  it should "handle token request failures" in {
    val endpoint = "http://localhost/token"
    val provider = createProvider(endpoint)

    provider
      .request(scope = client)
      .map { response =>
        fail(s"Unexpected response received: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should startWith("Failed to retrieve token")
          e.getMessage should include("Connection refused")
      }
  }

  it should "handle failures during token unmarshalling" in {
    val endpoint = createEndpoint(port = ports.dequeue())
    endpoint.start()

    val provider = createProvider(endpoint = s"${endpoint.url}/token/invalid")

    provider
      .request(scope = client)
      .map { response =>
        fail(s"Unexpected response received: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          endpoint.stop()
          e.getMessage should startWith("Failed to unmarshal response [200 OK]")
          e.getMessage should include("Unsupported Content-Type")
      }
  }

  it should "handle unexpected token endpoint responses" in {
    val endpoint = createEndpoint(port = ports.dequeue())
    endpoint.start()

    val tokenEndpoint = s"${endpoint.url}/token/error"
    val provider = createProvider(endpoint = tokenEndpoint)

    provider
      .request(scope = client)
      .map { response =>
        fail(s"Unexpected response received: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          endpoint.stop()
          e.getMessage should startWith(
            s"Token retrieval from [$tokenEndpoint] failed with [500 Internal Server Error]"
          )
      }
  }

  it should "successfully provide cached tokens" in {
    val endpoint = createEndpoint(port = ports.dequeue())
    endpoint.start()

    val provider = createProvider(endpoint = s"${endpoint.url}/token")

    for {
      response1 <- provider.provide(scope = client)
      response2 <- provider.provide(scope = client)
      response3 <- provider.provide(scope = client)
    } yield {
      endpoint.stop()

      endpoint.count("/token") should be(1)

      response1 should not be empty
      response2 should not be empty
      response3 should not be empty
    }
  }

  it should "expire cached tokens" in {
    val expiration = 1.second
    val tolerance = 800.millis
    val waitDelay = ((expiration - tolerance) * 1.5).toMillis.millis

    val endpoint = createEndpoint(port = ports.dequeue(), expirationSeconds = 1)
    endpoint.start()

    val provider = createProvider(
      endpoint = s"${endpoint.url}/token",
      expirationTolerance = 800.millis
    )

    for {
      response1 <- provider.provide(scope = client)
      response2 <- provider.provide(scope = client)
      response3 <- provider.provide(scope = client)
      _ <- akka.pattern.after(waitDelay, system.scheduler)(Future.successful(Done))
      response4 <- provider.provide(scope = client)
      response5 <- provider.provide(scope = client)
    } yield {
      endpoint.stop()

      endpoint.count("/token") should be(2)

      response1 should not be empty
      response2 should not be empty
      response3 should not be empty
      response4 should not be empty
      response5 should not be empty
    }
  }

  it should "support custom connection contexts" in {
    val config: Config = ConfigFactory.load().getConfig("stasis.test.core.security.tls")

    val serverContextConfig = EndpointContext.ContextConfig(config.getConfig("context-server-jks"))

    val clientContext = EndpointContext.create(
      contextConfig = EndpointContext.ContextConfig(config.getConfig("context-client"))
    )

    val expiration = 42

    val endpoint = createEndpoint(
      port = ports.dequeue(),
      expirationSeconds = expiration,
      keystoreConfig = serverContextConfig.keyStoreConfig
    )

    endpoint.start()

    val provider = createProvider(endpoint = s"${endpoint.url}/token", context = Some(clientContext))

    provider
      .request(scope = client)
      .map { response =>
        endpoint.stop()

        endpoint.count("/token") should be(1)

        response.access_token should not be empty
        response.expires_in should be(expiration)
        response.scope should be(Some(client))
      }
  }

  private def createProvider(
    endpoint: String,
    expirationTolerance: FiniteDuration = 3.seconds,
    context: Option[HttpsConnectionContext] = None
  ): JwtProvider = new JwtProvider(
    tokenEndpoint = endpoint,
    client = client,
    clientSecret = clientSecret,
    expirationTolerance = expirationTolerance,
    context = context
  )

  private def createEndpoint(
    port: Int,
    expirationSeconds: Int = 6000,
    keystoreConfig: Option[EndpointContext.StoreConfig] = None
  ): MockJwtEndpoint = new MockJwtEndpoint(
    port = port,
    subject = client,
    secret = clientSecret,
    expirationSeconds = expirationSeconds,
    signatureKey = MockJwksGenerators.generateRandomRsaKey(keyId = Some("rsa-0")),
    withKeystoreConfig = keystoreConfig
  )

  private implicit val system: ActorSystem[SpawnProtocol] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol.behavior): Behavior[SpawnProtocol],
    "JwtProviderSpec"
  )

  private implicit val untypedSystem: akka.actor.ActorSystem = system.toUntyped
  private implicit val mat: ActorMaterializer = ActorMaterializer()

  private val ports: mutable.Queue[Int] = (26000 to 26100).to[mutable.Queue]

  private val client: String = "some-client"
  private val clientSecret: String = "some-client-secret"

  override protected def afterAll(): Unit =
    system.terminate()
}
