package stasis.test.specs.unit.client.service.components

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.util.ByteString
import org.slf4j.{Logger, LoggerFactory}
import stasis.client.service.{ApplicationDirectory, ApplicationTray}
import stasis.client.service.components.exceptions.ServiceStartupFailure
import stasis.client.service.components.{Base, Files, Init, Secrets}
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.mocks.MockTokenEndpoint
import stasis.test.specs.unit.client.{EncodingHelpers, ResourceHelpers}

import java.util.UUID
import scala.collection.mutable
import scala.concurrent.Future
import scala.util.control.NonFatal

class SecretsSpec extends AsyncUnitSpec with ResourceHelpers with EncodingHelpers {
  "A Secrets component" should "create itself from config" in {
    val tokenEndpointPort = ports.dequeue()
    val endpoint = MockTokenEndpoint(port = tokenEndpointPort, token = "test-token")
    endpoint.start()

    Secrets(
      base = Base(
        applicationDirectory = createCustomApplicationDirectory(tokenEndpointPort),
        applicationTray = ApplicationTray.NoOp(),
        terminate = () => ()
      ).await,
      init = new Init {
        override def credentials(): Future[(String, Array[Char])] = Future.successful((username, password))
      }
    ).map { secrets =>
      endpoint.stop()
      secrets.deviceSecret.user should be(expectedUser)
      secrets.deviceSecret.device should be(expectedDevice)
    }
  }

  it should "handle credentials retrieval failures" in {
    val tokenEndpointPort = ports.dequeue()
    val endpoint = MockTokenEndpoint(port = tokenEndpointPort, token = "test-token")
    endpoint.start()

    Secrets(
      base = Base(
        applicationDirectory = createCustomApplicationDirectory(tokenEndpointPort),
        applicationTray = ApplicationTray.NoOp(),
        terminate = () => ()
      ).await,
      init = new Init {
        override def credentials(): Future[(String, Array[Char])] = Future.failed(new RuntimeException("test failure"))
      }
    ).map { result =>
      fail(s"Unexpected result received: [$result]")
    }.recover { case NonFatal(e: ServiceStartupFailure) =>
      endpoint.stop()

      e.cause should be("credentials")
      e.message should be("RuntimeException: test failure")
    }
  }

  it should "handle device secret file retrieval failures" in {
    val tokenEndpointPort = ports.dequeue()
    val endpoint = MockTokenEndpoint(port = tokenEndpointPort, token = "test-token")
    endpoint.start()

    Secrets(
      base = Base(
        applicationDirectory = createApplicationDirectory(init = _ => ()),
        applicationTray = ApplicationTray.NoOp(),
        terminate = () => ()
      ).await,
      init = new Init {
        override def credentials(): Future[(String, Array[Char])] = Future.successful((username, password))
      }
    ).map { result =>
      fail(s"Unexpected result received: [$result]")
    }.recover { case NonFatal(e: ServiceStartupFailure) =>
      endpoint.stop()

      e.cause should be("file")
      e.message should include(s"FileNotFoundException: File [${Files.DeviceSecret}] not found")
    }
  }

  it should "handle device secret file decryption failures" in {
    val tokenEndpointPort = ports.dequeue()
    val endpoint = MockTokenEndpoint(port = tokenEndpointPort, token = "test-token")
    endpoint.start()

    Secrets(
      base = Base(
        applicationDirectory = createCustomApplicationDirectory(tokenEndpointPort, deviceSecret = ByteString("invalid")),
        applicationTray = ApplicationTray.NoOp(),
        terminate = () => ()
      ).await,
      init = new Init {
        override def credentials(): Future[(String, Array[Char])] = Future.successful((username, password))
      }
    ).map { result =>
      fail(s"Unexpected result received: [$result]")
    }.recover { case NonFatal(e: ServiceStartupFailure) =>
      endpoint.stop()

      e.cause should be("credentials")
      e.message should include("Output buffer invalid")
    }
  }

  it should "handle core token retrieval failures" in {
    val tokenEndpointPort = ports.dequeue()
    val allowedGrants = Seq("password") // API token retrieval IS allowed; core token retrieval NOT allowed
    val endpoint = MockTokenEndpoint(port = tokenEndpointPort, token = "test-token", allowedGrants = allowedGrants)
    endpoint.start()

    Secrets(
      base = Base(
        applicationDirectory = createCustomApplicationDirectory(tokenEndpointPort),
        applicationTray = ApplicationTray.NoOp(),
        terminate = () => ()
      ).await,
      init = new Init {
        override def credentials(): Future[(String, Array[Char])] = Future.successful((username, password))
      }
    ).map { result =>
      fail(s"Unexpected result received: [$result]")
    }.recover { case NonFatal(e: ServiceStartupFailure) =>
      endpoint.stop()

      e.cause should be("token")
      e.message should include("400 Bad Request")
    }
  }

  it should "handle API token retrieval failures" in {
    val tokenEndpointPort = ports.dequeue()
    val allowedGrants = Seq("client_credentials") // API token retrieval NOT allowed; core token retrieval IS allowed
    val endpoint = MockTokenEndpoint(port = tokenEndpointPort, token = "test-token", allowedGrants = allowedGrants)
    endpoint.start()

    Secrets(
      base = Base(
        applicationDirectory = createCustomApplicationDirectory(tokenEndpointPort),
        applicationTray = ApplicationTray.NoOp(),
        terminate = () => ()
      ).await,
      init = new Init {
        override def credentials(): Future[(String, Array[Char])] = Future.successful((username, password))
      }
    ).map { result =>
      fail(s"Unexpected result received: [$result]")
    }.recover { case NonFatal(e: ServiceStartupFailure) =>
      endpoint.stop()

      e.cause should be("token")
      e.message should include("400 Bad Request")
    }
  }

  private def createCustomApplicationDirectory(tokenEndpointPort: Int): ApplicationDirectory =
    createCustomApplicationDirectory(tokenEndpointPort, encryptedDeviceSecret)

  private def createCustomApplicationDirectory(tokenEndpointPort: Int, deviceSecret: ByteString): ApplicationDirectory =
    createApplicationDirectory(
      init = dir => {
        val path = dir.config.get
        java.nio.file.Files.createDirectories(path)
        java.nio.file.Files.write(path.resolve(Files.DeviceSecret), deviceSecret.toArray)
        java.nio.file.Files.writeString(
          path.resolve(Files.ConfigOverride),
          s"""{$tokenEndpointConfigEntry: "http://localhost:$tokenEndpointPort/oauth/token"}"""
        )
      }
    )

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "SecretsSpec"
  )

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private val ports: mutable.Queue[Int] = (29000 to 29100).to(mutable.Queue)

  private val encryptedDeviceSecret = "08ko0LWDalPvEHna/WWuq3LoEaA3m4dQ1QuP".decodeFromBase64 // decrypted == "test-secret"

  private val username = "test-user"
  private val password = "test-password".toCharArray
  private val expectedUser = UUID.fromString("3256119f-068c-4c17-9184-2ec46f48ca54")
  private val expectedDevice = UUID.fromString("bc3b2b9a-3d04-4c8c-a6bb-b4ee428d1a99")

  private val tokenEndpointConfigEntry = "stasis.client.server.authentication.token-endpoint"
}
