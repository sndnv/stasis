package stasis.test.specs.unit.server.security.devices

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import stasis.core.security.jwt.JwtProvider
import stasis.core.security.tls.EndpointContext
import stasis.server.security.devices.IdentityDeviceCredentialsManager
import stasis.shared.model.devices.Device
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.server.security.mocks.MockIdentityManageEndpoint
import stasis.test.specs.unit.server.security.mocks.MockIdentityManageEndpoint.{CreationResult, SearchResult, UpdateResult}
import stasis.test.specs.unit.shared.model.Generators

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

class IdentityDeviceCredentialsManagerSpec extends AsyncUnitSpec {
  "An IdentityDeviceCredentialsManager" should "set client secrets for new devices" in {
    val endpoint = MockIdentityManageEndpoint(port = ports.dequeue(), credentials = credentials, existingDevice = None)
    endpoint.start()

    val manager = createManager(endpoint.url)

    manager
      .setClientSecret(device = device, clientSecret = testSecret)
      .map { _ =>
        endpoint.stop()

        endpoint.created should be(1)
        endpoint.updated should be(0)
        endpoint.searched should be(1)
      }
  }

  it should "reset secrets for existing devices" in {
    val endpoint = MockIdentityManageEndpoint(port = ports.dequeue(), credentials = credentials, existingDevice = Some(device))
    endpoint.start()

    val manager = createManager(endpoint.url)

    manager
      .setClientSecret(device = device, clientSecret = testSecret)
      .map { _ =>
        endpoint.stop()

        endpoint.created should be(0)
        endpoint.updated should be(1)
        endpoint.searched should be(1)
      }
  }

  it should "fail to set client credentials if more than one client matches an existing device" in {
    val endpoint = MockIdentityManageEndpoint(
      port = ports.dequeue(),
      credentials = credentials,
      existingDevice = Some(device),
      searchResult = SearchResult.MultiSubject
    )

    endpoint.start()

    val manager = createManager(endpoint.url)

    manager
      .setClientSecret(device = device, clientSecret = testSecret)
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover {
        case NonFatal(e) =>
          endpoint.stop()

          e.getMessage should startWith(
            s"Expected only one client to match node [${device.node}] but [2] clients found"
          )

          endpoint.created should be(0)
          endpoint.updated should be(0)
          endpoint.searched should be(1)
      }
  }

  it should "handle invalid responses" in {
    val endpoint = MockIdentityManageEndpoint(
      port = ports.dequeue(),
      credentials = credentials,
      existingDevice = Some(device),
      searchResult = SearchResult.Invalid
    )

    endpoint.start()

    val manager = createManager(endpoint.url)

    manager
      .setClientSecret(device = device, clientSecret = testSecret)
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover {
        case NonFatal(e) =>
          endpoint.stop()

          e.getMessage should startWith("Identity response unmarshalling failed")

          endpoint.created should be(0)
          endpoint.updated should be(0)
          endpoint.searched should be(1)
      }
  }

  it should "handle existing client/device query failures" in {
    val endpoint = MockIdentityManageEndpoint(
      port = ports.dequeue(),
      credentials = credentials,
      existingDevice = Some(device),
      searchResult = SearchResult.Failure
    )

    endpoint.start()

    val manager = createManager(endpoint.url)

    manager
      .setClientSecret(device = device, clientSecret = testSecret)
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover {
        case NonFatal(e) =>
          endpoint.stop()

          e.getMessage should startWith("Identity request failed with [500 Internal Server Error]")

          endpoint.created should be(0)
          endpoint.updated should be(0)
          endpoint.searched should be(1)
      }
  }

  it should "handle new client/device creation failures" in {
    val endpoint = MockIdentityManageEndpoint(
      port = ports.dequeue(),
      credentials = credentials,
      existingDevice = None,
      creationResult = CreationResult.Failure
    )

    endpoint.start()

    val manager = createManager(endpoint.url)

    manager
      .setClientSecret(device = device, clientSecret = testSecret)
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover {
        case NonFatal(e) =>
          endpoint.stop()

          e.getMessage should startWith("Identity request failed with [500 Internal Server Error]")

          endpoint.created should be(1)
          endpoint.updated should be(0)
          endpoint.searched should be(1)
      }
  }

  it should "handle existing client/device update failures" in {
    val endpoint = MockIdentityManageEndpoint(
      port = ports.dequeue(),
      credentials = credentials,
      existingDevice = Some(device),
      updateResult = UpdateResult.Failure
    )

    endpoint.start()

    val manager = createManager(endpoint.url)

    manager
      .setClientSecret(device = device, clientSecret = testSecret)
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover {
        case NonFatal(e) =>
          endpoint.stop()

          e.getMessage should startWith("Identity request failed with [500 Internal Server Error]")

          endpoint.created should be(0)
          endpoint.updated should be(1)
          endpoint.searched should be(1)
      }
  }

  "An IdentityDeviceCredentialsManager Default CredentialsProvider" should "provide credentials" in {
    val expectedToken = "test-token"
    val expectedScope = "test-scope"

    val underlying = new JwtProvider {
      override def provide(scope: String): Future[String] = Future.successful(s"$expectedToken;$scope")
    }

    val provider = IdentityDeviceCredentialsManager.CredentialsProvider.Default(
      scope = expectedScope,
      underlying = underlying
    )

    provider.provide().map { credentials =>
      credentials.token() should be(
        s"$expectedToken;$expectedScope"
      )
    }
  }

  private def createManager(
    identityUrl: String,
    context: Option[EndpointContext] = None
  ): IdentityDeviceCredentialsManager =
    new IdentityDeviceCredentialsManager(
      identityUrl = identityUrl,
      identityCredentials = () => Future.successful(credentials),
      redirectUri = "http://localhost:1234/redirect",
      tokenExpiration = 1.hour,
      context = context,
      requestBufferSize = 100
    )

  private val device: Device = Generators.generateDevice

  private val testSecret: String = "test-secret"

  private val credentials = OAuth2BearerToken(token = "test-token")

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "IdentityDeviceCredentialsManagerSpec"
  )

  private val ports: mutable.Queue[Int] = (33000 to 33100).to(mutable.Queue)
}
