package stasis.test.specs.unit.client.service.components

import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.model.headers.HttpCredentials
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken
import org.apache.pekko.util.ByteString
import org.scalatest.Assertion
import org.scalatest.concurrent.Eventually
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.client.api.clients.Clients
import stasis.client.encryption.Aes
import stasis.client.encryption.secrets.DeviceSecret
import stasis.client.security.CredentialsProvider
import stasis.client.service.ApplicationTray
import stasis.client.service.components.ApiClients
import stasis.client.service.components.Base
import stasis.client.service.components.Ops
import stasis.client.service.components.Secrets
import stasis.client.service.components.Tracking
import stasis.core.commands.proto.Command
import stasis.core.commands.proto.CommandParameters
import stasis.core.commands.proto.CommandSource
import stasis.core.commands.proto.LogoutUser
import stasis.shared.model.devices.Device
import stasis.shared.model.users.User
import stasis.shared.secrets.SecretsConfig
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers
import stasis.test.specs.unit.client.mocks.MockServerApiEndpointClient
import stasis.test.specs.unit.client.mocks.MockServerCoreEndpointClient

class OpsSpec extends AsyncUnitSpec with ResourceHelpers with Eventually {
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

        override def config: SecretsConfig = secretsConfig

        override def verifyUserPassword: Array[Char] => Boolean = _ => false

        override def updateUserCredentials: (Clients, Array[Char], String) => Future[Done] =
          (_, _, _) => Future.successful(Done)

        override def reEncryptDeviceSecret: (Clients, Array[Char]) => Future[Done] =
          (_, _) => Future.successful(Done)
      }
    ).map { ops =>
      ops.executor.active.await shouldBe empty
      ops.scheduler.schedules.await shouldBe empty
    }
  }

  it should "support executing logout_user commands" in {
    val directory = createApplicationDirectory(
      init = dir => {
        val path = dir.config.get
        java.nio.file.Files.createDirectories(path)
        java.nio.file.Files.createFile(path.resolve("client.rules"))
        java.nio.file.Files.createFile(path.resolve("client.schedules"))
      }
    )

    val terminationCount: AtomicInteger = new AtomicInteger(0)

    val base = Base(
      applicationDirectory = directory,
      applicationTray = ApplicationTray.NoOp(),
      terminate = () => {
        val _ = terminationCount.incrementAndGet()
      }
    ).await

    val command = Command(
      sequenceId = 1,
      source = CommandSource.User,
      target = None,
      parameters = LogoutUser(reason = Some("Test reason")),
      created = Instant.now()
    )

    noException should be thrownBy Ops.executeCommand(base, command = command).await

    eventually[Assertion] {
      terminationCount.get() should be(1)
    }

    noException should be thrownBy Ops.executeCommand(base, command = command.copy(parameters = LogoutUser(reason = None))).await

    eventually[Assertion] {
      terminationCount.get() should be(2)
    }
  }

  it should "handle unsupported commands" in {
    val directory = createApplicationDirectory(
      init = dir => {
        val path = dir.config.get
        java.nio.file.Files.createDirectories(path)
        java.nio.file.Files.createFile(path.resolve("client.rules"))
        java.nio.file.Files.createFile(path.resolve("client.schedules"))
      }
    )

    val terminationCount: AtomicInteger = new AtomicInteger(0)

    val base = Base(
      applicationDirectory = directory,
      applicationTray = ApplicationTray.NoOp(),
      terminate = () => {
        val _ = terminationCount.incrementAndGet()
      }
    ).await

    val command = Command(
      sequenceId = 1,
      source = CommandSource.User,
      target = None,
      parameters = CommandParameters.Empty,
      created = Instant.now()
    )

    noException should be thrownBy Ops.executeCommand(base, command = command).await

    await(delay = 300.millis, withSystem = typedSystem)

    terminationCount.get() should be(0)
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "OpsSpec"
  )

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 250.milliseconds)

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)
}
