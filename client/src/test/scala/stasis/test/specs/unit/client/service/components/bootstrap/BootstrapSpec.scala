package stasis.test.specs.unit.client.service.components.bootstrap

import java.util.UUID

import scala.collection.mutable

import com.typesafe.config.Config
import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.mockito.Strictness
import org.mockito.scalatest.AsyncMockitoSugar
import org.slf4j.Logger
import play.api.libs.json.Json

import stasis.client.service.ApplicationArguments
import stasis.client.service.components.bootstrap.Base
import stasis.client.service.components.bootstrap.Bootstrap
import stasis.client.service.components.bootstrap.Init
import stasis.core.routing.Node
import io.github.sndnv.layers.security.tls.EndpointContext
import stasis.shared.model.devices.Device
import stasis.shared.model.devices.DeviceBootstrapParameters
import stasis.shared.model.users.User
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.Fixtures
import stasis.test.specs.unit.client.ResourceHelpers
import stasis.test.specs.unit.client.mocks.MockServerBootstrapEndpoint

class BootstrapSpec extends AsyncUnitSpec with ResourceHelpers with AsyncMockitoSugar {
  "A Bootstrap component" should "support executing device bootstrap" in {
    implicit val logger: Logger = mock[Logger]

    val config: Config = typedSystem.settings.config.getConfig("stasis.test.client.security.tls")

    val endpointContext = EndpointContext(
      config = EndpointContext.Config(config.getConfig("context-server"))
    )

    val endpointPort = ports.dequeue()

    val endpoint = new MockServerBootstrapEndpoint(expectedCode = testCode, providedParams = testParams)
    endpoint.start(port = endpointPort, context = Some(endpointContext))

    val modeArguments = ApplicationArguments.Mode.Bootstrap(
      serverBootstrapUrl = s"https://localhost:$endpointPort",
      bootstrapCode = testCode,
      acceptSelfSignedCertificates = true,
      userName = "test-user",
      userPassword = Array.emptyCharArray,
      recreateFiles = false
    )

    for {
      base <- Base(modeArguments = modeArguments, applicationDirectory = createApplicationDirectory(init = _ => ()))
      init <- Init(base, console = None)
      bootstrap <- Bootstrap(base, init)
      actualParams <- bootstrap.execute()
    } yield {
      verify(logger).debug(
        "Console not available; using CLI-based bootstrap..."
      )

      verify(logger).infoN(
        "Executing client bootstrap using server [{}]...",
        modeArguments.serverBootstrapUrl
      )

      verify(logger).infoN(
        "Server [{}] successfully processed bootstrap request",
        modeArguments.serverBootstrapUrl
      )

      actualParams should be(testParams)
    }
  }

  it should "log bootstrap execution failures" in {
    implicit val logger: Logger = mock[Logger](withSettings.strictness(Strictness.Lenient))

    val modeArguments = ApplicationArguments.Mode.Bootstrap(
      serverBootstrapUrl = "https://localhost:1234",
      bootstrapCode = testCode,
      acceptSelfSignedCertificates = true,
      userName = "test-user",
      userPassword = Array.emptyCharArray,
      recreateFiles = false
    )

    val result = for {
      base <- Base(modeArguments = modeArguments, applicationDirectory = createApplicationDirectory(init = _ => ()))
      init <- Init(base, console = None)
      bootstrap <- Bootstrap(base, init)
      _ <- bootstrap.execute()
    } yield {
      Done
    }

    result.failed
      .map { e =>
        verify(logger).debug(
          "Console not available; using CLI-based bootstrap..."
        )

        verify(logger).infoN(
          "Executing client bootstrap using server [{}]...",
          modeArguments.serverBootstrapUrl
        )

        e.getMessage should include("Connection refused")
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

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "BootstrapSpec"
  )

  private val ports: mutable.Queue[Int] = (35000 to 35100).to(mutable.Queue)
}
