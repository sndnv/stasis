package stasis.test.specs.unit.client.service.components.bootstrap

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import com.typesafe.config.Config
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.Json
import stasis.client.service.ApplicationArguments
import stasis.client.service.components.bootstrap.{Base, Bootstrap, Init}
import stasis.core.routing.Node
import stasis.core.security.tls.EndpointContext
import stasis.shared.model.devices.{Device, DeviceBootstrapParameters}
import stasis.shared.model.users.User
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.{Fixtures, ResourceHelpers}
import stasis.test.specs.unit.client.mocks.MockServerBootstrapEndpoint

import java.util.UUID
import scala.collection.mutable

class BootstrapSpec extends AsyncUnitSpec with ResourceHelpers {
  "A Bootstrap component" should "support executing device bootstrap" in {
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
      userPassword = Array.emptyCharArray
    )

    for {
      base <- Base(modeArguments = modeArguments, applicationDirectory = createApplicationDirectory(init = _ => ()))
      init <- Init(base, console = None)
      bootstrap <- Bootstrap(base, init)
      actualParams <- bootstrap.execute()
    } yield {
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
      context = DeviceBootstrapParameters.Context.disabled()
    ),
    serverApi = DeviceBootstrapParameters.ServerApi(
      url = "http://localhost:5678",
      user = User.generateId().toString,
      userSalt = "test-salt",
      device = Device.generateId().toString,
      context = DeviceBootstrapParameters.Context.disabled()
    ),
    serverCore = DeviceBootstrapParameters.ServerCore(
      address = "http://localhost:5679",
      nodeId = Node.generateId().toString,
      context = DeviceBootstrapParameters.Context.disabled()
    ),
    secrets = Fixtures.Secrets.DefaultConfig,
    additionalConfig = Json.obj()
  )

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "BootstrapSpec"
  )

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private val ports: mutable.Queue[Int] = (35000 to 35100).to(mutable.Queue)
}
