package stasis.test.specs.unit.server.security.devices

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken

import stasis.core.api.PoolClient
import stasis.layers.security.tls.EndpointContext
import stasis.server.security.devices.IdentityDeviceCredentialsManager
import stasis.shared.model.devices.Device
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.server.security.mocks.MockIdentityDeviceManageEndpoint
import stasis.test.specs.unit.server.security.mocks.MockIdentityDeviceManageEndpoint.CreationResult
import stasis.test.specs.unit.server.security.mocks.MockIdentityDeviceManageEndpoint.SearchResult
import stasis.test.specs.unit.server.security.mocks.MockIdentityDeviceManageEndpoint.UpdateResult
import stasis.test.specs.unit.shared.model.Generators

class IdentityDeviceCredentialsManagerSpec extends AsyncUnitSpec {
  "An IdentityDeviceCredentialsManager" should "set client secrets for new devices" in {
    val endpoint = MockIdentityDeviceManageEndpoint(port = ports.dequeue(), credentials = credentials, existingDevice = None)
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
    val endpoint =
      MockIdentityDeviceManageEndpoint(port = ports.dequeue(), credentials = credentials, existingDevice = Some(device))
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
    val endpoint = MockIdentityDeviceManageEndpoint(
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
      .recover { case NonFatal(e) =>
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
    val endpoint = MockIdentityDeviceManageEndpoint(
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
      .recover { case NonFatal(e) =>
        endpoint.stop()

        e.getMessage should startWith("Identity response unmarshalling failed")

        endpoint.created should be(0)
        endpoint.updated should be(0)
        endpoint.searched should be(1)
      }
  }

  it should "handle existing client/device query failures" in {
    val endpoint = MockIdentityDeviceManageEndpoint(
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
      .recover { case NonFatal(e) =>
        endpoint.stop()

        e.getMessage should startWith("Identity request failed with [500 Internal Server Error]")

        endpoint.created should be(0)
        endpoint.updated should be(0)
        endpoint.searched should be(3) // first attempt + 2 retries
      }
  }

  it should "handle new client/device creation failures" in {
    val endpoint = MockIdentityDeviceManageEndpoint(
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
      .recover { case NonFatal(e) =>
        endpoint.stop()

        e.getMessage should startWith("Identity request failed with [500 Internal Server Error]")

        endpoint.created should be(3) // first attempt + 2 retries
        endpoint.updated should be(0)
        endpoint.searched should be(1)
      }
  }

  it should "handle existing client/device update failures" in {
    val endpoint = MockIdentityDeviceManageEndpoint(
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
      .recover { case NonFatal(e) =>
        endpoint.stop()

        e.getMessage should startWith("Identity request failed with [500 Internal Server Error]")

        endpoint.created should be(0)
        endpoint.updated should be(3) // first attempt + 2 retries
        endpoint.searched should be(1)
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
      context = context
    ) {
      override protected def config: PoolClient.Config = PoolClient.Config.Default.copy(
        minBackoff = 10.millis,
        maxBackoff = 20.milli,
        maxRetries = 2
      )
    }

  private val device: Device = Generators.generateDevice

  private val testSecret: String = "test-secret"

  private val credentials = OAuth2BearerToken(token = "test-token")

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "IdentityDeviceCredentialsManagerSpec"
  )

  private val ports: mutable.Queue[Int] = (33000 to 33100).to(mutable.Queue)
}
