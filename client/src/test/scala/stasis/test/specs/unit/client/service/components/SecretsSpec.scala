package stasis.test.specs.unit.client.service.components

import java.util.UUID

import scala.collection.mutable
import scala.concurrent.Future
import scala.util.control.NonFatal

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.util.ByteString
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.client.api.clients.Clients
import stasis.client.service.ApplicationDirectory
import stasis.client.service.ApplicationTray
import stasis.client.service.components.Base
import stasis.client.service.components.Files
import stasis.client.service.components.Init
import stasis.client.service.components.Secrets
import stasis.client.service.components.exceptions.ServiceStartupFailure
import stasis.client.service.components.internal.ConfigOverride
import stasis.shared.model.devices.Device
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.EncodingHelpers
import stasis.test.specs.unit.client.ResourceHelpers
import stasis.test.specs.unit.client.mocks.MockServerApiEndpointClient
import stasis.test.specs.unit.client.mocks.MockTokenEndpoint

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

  it should "support verifying user password" in {
    val tokenEndpointPort = ports.dequeue()
    val endpoint = MockTokenEndpoint(port = tokenEndpointPort, token = "test-token")
    endpoint.start()

    val directory = createCustomApplicationDirectory(tokenEndpointPort)

    Secrets(
      base = Base(
        applicationDirectory = directory,
        applicationTray = ApplicationTray.NoOp(),
        terminate = () => ()
      ).await,
      init = new Init {
        override def credentials(): Future[(String, Array[Char])] = Future.successful((username, password))
      }
    ).map { secrets =>
      endpoint.stop()

      secrets.verifyUserPassword(password) should be(true)
      secrets.verifyUserPassword("other-password".toCharArray) should be(false)
    }
  }

  it should "support updating user credentials (and replace existing key on server)" in {
    val tokenEndpointPort = ports.dequeue()
    val endpoint = MockTokenEndpoint(port = tokenEndpointPort, token = "test-token")
    endpoint.start()

    val apiClient = new MockServerApiEndpointClient(self = Device.generateId()) {
      override def deviceKeyExists(): Future[Boolean] = Future.successful(true)
    }

    val directory = createCustomApplicationDirectory(tokenEndpointPort)

    for {
      base <- Base(applicationDirectory = directory, applicationTray = ApplicationTray.NoOp(), terminate = () => ())
      secrets <- Secrets(base = base, init = () => Future.successful((username, password)))
      currentPasswordValidBeforeUpdate = secrets.verifyUserPassword(password)
      newPasswordValidBeforeUpdate = secrets.verifyUserPassword(newPassword)
      existingDeviceSecret <- directory.pullFile[ByteString](Files.DeviceSecret)
      existingSalt = ConfigOverride.load(directory).getString(userSaltConfigEntry)
      _ <- secrets.updateUserCredentials(Clients(api = apiClient, core = null), newPassword, newSalt)
      updatedDeviceSecret <- directory.pullFile[ByteString](Files.DeviceSecret)
      updatedSalt = ConfigOverride.load(directory).getString(userSaltConfigEntry)
      currentPasswordValidAfterUpdate = secrets.verifyUserPassword(password)
      newPasswordValidAfterUpdate = secrets.verifyUserPassword(newPassword)
    } yield {
      endpoint.stop()

      currentPasswordValidBeforeUpdate should be(true)
      newPasswordValidBeforeUpdate should be(false)
      existingSalt should be("test-salt")
      existingDeviceSecret should be(encryptedDeviceSecret)

      updatedSalt should be(newSalt)
      updatedDeviceSecret should be(reEncryptedDeviceSecret)
      currentPasswordValidAfterUpdate should be(false)
      newPasswordValidAfterUpdate should be(true)

      apiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPushed) should be(1)
    }
  }

  it should "support updating user credentials (and not replace non-existing key on server)" in {
    val tokenEndpointPort = ports.dequeue()
    val endpoint = MockTokenEndpoint(port = tokenEndpointPort, token = "test-token")
    endpoint.start()

    val apiClient = new MockServerApiEndpointClient(self = Device.generateId()) {
      override def deviceKeyExists(): Future[Boolean] = Future.successful(false)
    }

    val directory = createCustomApplicationDirectory(tokenEndpointPort)

    for {
      base <- Base(applicationDirectory = directory, applicationTray = ApplicationTray.NoOp(), terminate = () => ())
      secrets <- Secrets(base = base, init = () => Future.successful((username, password)))
      currentPasswordValidBeforeUpdate = secrets.verifyUserPassword(password)
      newPasswordValidBeforeUpdate = secrets.verifyUserPassword(newPassword)
      existingDeviceSecret <- directory.pullFile[ByteString](Files.DeviceSecret)
      existingSalt = ConfigOverride.load(directory).getString(userSaltConfigEntry)
      _ <- secrets.updateUserCredentials(Clients(api = apiClient, core = null), newPassword, newSalt)
      updatedDeviceSecret <- directory.pullFile[ByteString](Files.DeviceSecret)
      updatedSalt = ConfigOverride.load(directory).getString(userSaltConfigEntry)
      currentPasswordValidAfterUpdate = secrets.verifyUserPassword(password)
      newPasswordValidAfterUpdate = secrets.verifyUserPassword(newPassword)
    } yield {
      endpoint.stop()

      currentPasswordValidBeforeUpdate should be(true)
      newPasswordValidBeforeUpdate should be(false)
      existingSalt should be("test-salt")
      existingDeviceSecret should be(encryptedDeviceSecret)

      updatedSalt should be(newSalt)
      updatedDeviceSecret should be(reEncryptedDeviceSecret)
      currentPasswordValidAfterUpdate should be(false)
      newPasswordValidAfterUpdate should be(true)

      apiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPushed) should be(0)
    }
  }

  it should "support re-encrypting device secrets (and replace existing key on server)" in {
    val tokenEndpointPort = ports.dequeue()
    val endpoint = MockTokenEndpoint(port = tokenEndpointPort, token = "test-token")
    endpoint.start()

    val directory = createCustomApplicationDirectory(tokenEndpointPort)

    val apiClient = new MockServerApiEndpointClient(self = Device.generateId()) {
      override def deviceKeyExists(): Future[Boolean] = Future.successful(false)
    }

    for {
      base <- Base(applicationDirectory = directory, applicationTray = ApplicationTray.NoOp(), terminate = () => ())
      secrets <- Secrets(base = base, init = () => Future.successful((username, password)))
      existingDeviceSecret <- directory.pullFile[ByteString](Files.DeviceSecret)
      _ <- secrets.reEncryptDeviceSecret(Clients(api = apiClient, core = null), newPassword)
      updatedDeviceSecret <- directory.pullFile[ByteString](Files.DeviceSecret)
    } yield {
      endpoint.stop()

      existingDeviceSecret should be(encryptedDeviceSecret)
      updatedDeviceSecret should be(reEncryptedDeviceSecretWithExistingSalt)

      apiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPushed) should be(0)
    }
  }

  it should "support re-encrypting device secrets (and not replace non-existing key on server)" in {
    val tokenEndpointPort = ports.dequeue()
    val endpoint = MockTokenEndpoint(port = tokenEndpointPort, token = "test-token")
    endpoint.start()

    val directory = createCustomApplicationDirectory(tokenEndpointPort)

    val apiClient = new MockServerApiEndpointClient(self = Device.generateId()) {
      override def deviceKeyExists(): Future[Boolean] = Future.successful(true)
    }

    for {
      base <- Base(applicationDirectory = directory, applicationTray = ApplicationTray.NoOp(), terminate = () => ())
      secrets <- Secrets(base = base, init = () => Future.successful((username, password)))
      existingDeviceSecret <- directory.pullFile[ByteString](Files.DeviceSecret)
      _ <- secrets.reEncryptDeviceSecret(Clients(api = apiClient, core = null), newPassword)
      updatedDeviceSecret <- directory.pullFile[ByteString](Files.DeviceSecret)
    } yield {
      endpoint.stop()

      existingDeviceSecret should be(encryptedDeviceSecret)
      updatedDeviceSecret should be(reEncryptedDeviceSecretWithExistingSalt)

      apiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPushed) should be(1)
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
      e.message should include("Input data too short")
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
          s"""{$tokenEndpointConfigEntry: "http://localhost:$tokenEndpointPort/oauth/token", $userSaltConfigEntry: "test-salt"}"""
        )
      }
    )

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "ServiceSecretsSpec"
  )

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private val ports: mutable.Queue[Int] = (29000 to 29100).to(mutable.Queue)

  private val encryptedDeviceSecret =
    "08ko0LWDalPvEHna/WWuq3LoEaA3m4dQ1QuP".decodeFromBase64 // decrypted == "test-secret"

  private val reEncryptedDeviceSecret =
    "hU9e2iNzu8H3G5e4kmBMi4hMG3Y9ZCl2oYGG".decodeFromBase64 // decrypted == "test-secret"

  private val reEncryptedDeviceSecretWithExistingSalt =
    "6eLw3FNBRPoS6WDTeRbLxR5kuc6zBov9nuMX".decodeFromBase64 // decrypted == "test-secret"

  private val username = "test-user"
  private val password = "test-password".toCharArray
  private val newPassword = "new-password".toCharArray
  private val newSalt = "new-salt"
  private val expectedUser = UUID.fromString("3256119f-068c-4c17-9184-2ec46f48ca54")
  private val expectedDevice = UUID.fromString("bc3b2b9a-3d04-4c8c-a6bb-b4ee428d1a99")

  private val tokenEndpointConfigEntry = "stasis.client.server.authentication.token-endpoint"
  private val userSaltConfigEntry = "stasis.client.server.api.user-salt"
}
