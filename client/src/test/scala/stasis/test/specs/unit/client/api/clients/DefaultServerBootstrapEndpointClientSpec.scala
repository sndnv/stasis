package stasis.test.specs.unit.client.api.clients

import java.util.UUID

import scala.collection.mutable
import scala.util.control.NonFatal

import com.typesafe.config.Config
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.model.HttpResponse
import org.apache.pekko.http.scaladsl.model.StatusCodes
import play.api.libs.json.Json

import stasis.client.api.clients.DefaultServerBootstrapEndpointClient
import stasis.client.api.clients.exceptions.ServerBootstrapFailure
import stasis.core.routing.Node
import stasis.layers.security.tls.EndpointContext
import stasis.shared.model.devices.Device
import stasis.shared.model.devices.DeviceBootstrapParameters
import stasis.shared.model.users.User
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.Fixtures
import stasis.test.specs.unit.client.mocks.MockServerBootstrapEndpoint

class DefaultServerBootstrapEndpointClientSpec extends AsyncUnitSpec {
  "A DefaultServerBootstrapEndpointClient" should "execute device bootstrap" in {
    val endpointPort = ports.dequeue()
    val endpoint = new MockServerBootstrapEndpoint(expectedCode = testCode, providedParams = testParams)
    endpoint.start(port = endpointPort)

    val endpointClient = createClient(bootstrapPort = endpointPort)

    endpointClient
      .execute(testCode)
      .map { actualParams =>
        endpoint.bootstrapExecutedCount() should be(1)
        actualParams should be(testParams)
      }
  }

  it should "handle unexpected responses" in {
    import DefaultServerBootstrapEndpointClient._
    import stasis.shared.api.Formats._

    val response = HttpResponse(status = StatusCodes.OK, entity = "unexpected-response-entity")

    response
      .to[DeviceBootstrapParameters]
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e: ServerBootstrapFailure) =>
        e.getMessage should be(
          "Server bootstrap request unmarshalling failed with: [Unsupported Content-Type [Some(text/plain; charset=UTF-8)], supported: application/json]"
        )
      }
  }

  it should "handle endpoint failures" in {
    import DefaultServerBootstrapEndpointClient._
    import stasis.shared.api.Formats._

    val status = StatusCodes.NotFound
    val message = "Test Failure"
    val response = HttpResponse(status = status, entity = message)

    response
      .to[DeviceBootstrapParameters]
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e: ServerBootstrapFailure) =>
        e.getMessage should be(
          s"Server bootstrap request failed with [$status]: [$message]"
        )
      }
  }

  it should "fail if invalid TLS certificate encountered" in {
    val config: Config = typedSystem.settings.config.getConfig("stasis.test.client.security.tls")

    val endpointContext = EndpointContext(
      config = EndpointContext.Config(config.getConfig("context-server"))
    )

    val endpointPort = ports.dequeue()
    val endpoint = new MockServerBootstrapEndpoint(expectedCode = testCode, providedParams = testParams)
    endpoint.start(port = endpointPort, context = Some(endpointContext))

    val endpointClient = createClient(
      bootstrapPort = endpointPort,
      https = true
    )

    endpointClient
      .execute(testCode)
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e) =>
        endpoint.bootstrapExecutedCount() should be(0)
        e.getMessage should startWith("PKIX path building failed")
      }
  }

  it should "support accepting self-signed TLS certificate" in {
    val config: Config = typedSystem.settings.config.getConfig("stasis.test.client.security.tls")

    val endpointContext = EndpointContext(
      config = EndpointContext.Config(config.getConfig("context-server"))
    )

    val endpointPort = ports.dequeue()
    val endpoint = new MockServerBootstrapEndpoint(expectedCode = testCode, providedParams = testParams)
    endpoint.start(port = endpointPort, context = Some(endpointContext))

    val endpointClient = createClient(
      bootstrapPort = endpointPort,
      https = true,
      acceptSelfSignedCertificates = true
    )

    endpointClient
      .execute(testCode)
      .map { actualParams =>
        endpoint.bootstrapExecutedCount() should be(1)
        actualParams should be(testParams)
      }
  }

  private val testCode = "test-code"

  private val testParams = DeviceBootstrapParameters(
    authentication = DeviceBootstrapParameters.Authentication(
      tokenEndpoint = "http://localhost:1234",
      clientId = UUID.randomUUID().toString,
      clientSecret = "test-secret",
      useQueryString = true,
      scopes = DeviceBootstrapParameters.Scopes(
        api = "urn:stasis:identity:audience:server-api",
        core = s"urn:stasis:identity:audience:${Node.generateId().toString}"
      ),
      context = EndpointContext.Encoded.disabled()
    ),
    serverApi = DeviceBootstrapParameters.ServerApi(
      url = "http://localhost:5678",
      user = User.generateId().toString,
      userSalt = "test-salt",
      device = Device.generateId().toString,
      context = EndpointContext.Encoded.disabled()
    ),
    serverCore = DeviceBootstrapParameters.ServerCore(
      address = "http://localhost:5679",
      nodeId = Node.generateId().toString,
      context = EndpointContext.Encoded.disabled()
    ),
    secrets = Fixtures.Secrets.DefaultConfig,
    additionalConfig = Json.obj()
  )

  private def createClient(
    bootstrapPort: Int,
    https: Boolean = false,
    acceptSelfSignedCertificates: Boolean = false
  ): DefaultServerBootstrapEndpointClient =
    new DefaultServerBootstrapEndpointClient(
      serverBootstrapUrl = s"${if (https) "https" else "http"}://localhost:$bootstrapPort",
      acceptSelfSignedCertificates = acceptSelfSignedCertificates
    )

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "DefaultServerBootstrapEndpointClientSpec"
  )

  private val ports: mutable.Queue[Int] = (34000 to 34100).to(mutable.Queue)
}
