package stasis.test.specs.unit.client.service.components

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import org.apache.pekko.http.scaladsl.model.headers.{HttpCredentials, OAuth2BearerToken}
import org.apache.pekko.util.ByteString
import org.slf4j.{Logger, LoggerFactory}
import stasis.client.api.clients.Clients
import stasis.client.encryption.Aes
import stasis.client.encryption.secrets.DeviceSecret
import stasis.client.security.CredentialsProvider
import stasis.client.service.ApplicationTray
import stasis.client.service.components.{ApiClients, Base, Ops, Secrets, Tracking}
import stasis.shared.model.devices.Device
import stasis.shared.model.users.User
import stasis.shared.secrets.SecretsConfig
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers
import stasis.test.specs.unit.client.mocks.{MockServerApiEndpointClient, MockServerCoreEndpointClient}

import scala.concurrent.Future

class OpsSpec extends AsyncUnitSpec with ResourceHelpers {
  "An Ops component" should "create itself from config" in {
    implicit val secretsConfig: SecretsConfig = SecretsConfig(
      config = typedSystem.settings.config.getConfig("stasis.client.secrets"),
      ivSize = Aes.IvSize
    )

    val directory = createApplicationDirectory(
      init = dir => {
        val path = dir.config.get
        java.nio.file.Files.createDirectories(path)
        java.nio.file.Files.createFile(path.resolve("client.rules"))
        java.nio.file.Files.createFile(path.resolve("client.schedules"))
      }
    )

    val base = Base(applicationDirectory = directory, applicationTray = ApplicationTray.NoOp(), terminate = () => ()).await

    Ops(
      base = base,
      tracking = Tracking(base).await,
      apiClients = new ApiClients {
        override def clients: Clients =
          Clients(
            api = MockServerApiEndpointClient(),
            core = MockServerCoreEndpointClient()
          )
      },
      secrets = new Secrets {
        override def deviceSecret: DeviceSecret =
          DeviceSecret(
            user = User.generateId(),
            device = Device.generateId(),
            secret = ByteString.empty
          )

        override def credentialsProvider: CredentialsProvider =
          new CredentialsProvider {
            override def core: Future[HttpCredentials] = Future.successful(OAuth2BearerToken(token = "test-token"))
            override def api: Future[HttpCredentials] = Future.successful(OAuth2BearerToken(token = "test-token"))
          }
      }
    ).map { ops =>
      ops.executor.active.await shouldBe empty
      ops.scheduler.schedules.await shouldBe empty
    }
  }

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "OpsSpec"
  )

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)
}
