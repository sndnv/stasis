package stasis.test.specs.unit.client.service.components.maintenance

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

import com.google.common.jimfs.Jimfs
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.SpawnProtocol
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.util.ByteString
import org.apache.pekko.util.Timeout
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import stasis.client.service.ApplicationArguments
import stasis.client.service.ApplicationDirectory
import stasis.client.service.components.Files
import stasis.client.service.components.exceptions.ServiceStartupFailure
import stasis.client.service.components.internal.ConfigOverride
import stasis.client.service.components.maintenance.Base
import stasis.client.service.components.maintenance.Credentials
import stasis.client.service.components.maintenance.Init
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.EncodingHelpers
import stasis.test.specs.unit.client.ResourceHelpers

class CredentialsSpec extends AsyncUnitSpec with ResourceHelpers with EncodingHelpers {
  "A Credentials component" should "reset user credentials" in {
    val modeArguments = ApplicationArguments.Mode.Maintenance(
      regenerateApiCertificate = false,
      deviceSecretOperation = None,
      userCredentialsOperation = Some(ApplicationArguments.Mode.Maintenance.UserCredentialsOperation.Reset),
      currentUserName = currentUserName,
      currentUserPassword = currentUserPassword,
      newUserPassword = newUserPassword,
      newUserSalt = newUserSalt
    )

    val directory = createCustomApplicationDirectory(
      deviceSecret = Some(currentEncryptedDeviceSecret)
    )

    for {
      base <- Base(modeArguments = modeArguments, applicationDirectory = directory)
      init <- Init(base, console = None)
      credentials <- Credentials(base, init)
      existingUserSalt = ConfigOverride.load(directory).getString(userSaltConfigEntry)
      existingDeviceSecret <- directory.pullFile[ByteString](Files.DeviceSecret)
      _ <- credentials.apply()
      updatedUserSalt = ConfigOverride.load(directory).getString(userSaltConfigEntry)
      updatedDeviceSecret <- directory.pullFile[ByteString](Files.DeviceSecret)
    } yield {
      existingUserSalt should be(currentUserSalt)
      existingDeviceSecret should be(currentEncryptedDeviceSecret)
      updatedUserSalt should be(newUserSalt)
      updatedDeviceSecret should be(reEncryptedDeviceSecret)
    }
  }

  it should "handle current credentials retrieval failures" in {
    val modeArguments = ApplicationArguments.Mode.Maintenance(
      regenerateApiCertificate = false,
      deviceSecretOperation = None,
      userCredentialsOperation = Some(ApplicationArguments.Mode.Maintenance.UserCredentialsOperation.Reset),
      currentUserName = currentUserName,
      currentUserPassword = currentUserPassword,
      newUserPassword = newUserPassword,
      newUserSalt = newUserSalt
    )

    val directory = createCustomApplicationDirectory(
      deviceSecret = Some(currentEncryptedDeviceSecret)
    )

    Credentials(
      base = Base(modeArguments = modeArguments, applicationDirectory = directory).await,
      init = new Init {
        override def currentCredentials(): Future[(String, Array[Char])] =
          Future.failed(new RuntimeException("test failure"))

        override def newCredentials(): Future[(Array[Char], String)] =
          Future.successful((newUserPassword, newUserSalt))
      }
    )
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e: ServiceStartupFailure) =>
        e.cause should be("credentials")
        e.message should be("RuntimeException: test failure")
      }
  }

  it should "handle new credentials retrieval failures" in {
    val modeArguments = ApplicationArguments.Mode.Maintenance(
      regenerateApiCertificate = false,
      deviceSecretOperation = None,
      userCredentialsOperation = Some(ApplicationArguments.Mode.Maintenance.UserCredentialsOperation.Reset),
      currentUserName = currentUserName,
      currentUserPassword = currentUserPassword,
      newUserPassword = newUserPassword,
      newUserSalt = newUserSalt
    )

    val directory = createCustomApplicationDirectory(
      deviceSecret = Some(currentEncryptedDeviceSecret)
    )

    Credentials(
      base = Base(modeArguments = modeArguments, applicationDirectory = directory).await,
      init = new Init {
        override def currentCredentials(): Future[(String, Array[Char])] =
          Future.successful((currentUserName, currentUserPassword))

        override def newCredentials(): Future[(Array[Char], String)] =
          Future.failed(new RuntimeException("test failure"))
      }
    )
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e: ServiceStartupFailure) =>
        e.cause should be("credentials")
        e.message should be("RuntimeException: test failure")
      }
  }

  it should "handle failures when loading device secret from file" in {
    val modeArguments = ApplicationArguments.Mode.Maintenance(
      regenerateApiCertificate = false,
      deviceSecretOperation = None,
      userCredentialsOperation = Some(ApplicationArguments.Mode.Maintenance.UserCredentialsOperation.Reset),
      currentUserName = currentUserName,
      currentUserPassword = currentUserPassword,
      newUserPassword = newUserPassword,
      newUserSalt = newUserSalt
    )

    val filesystem = Jimfs.newFileSystem()

    val directory = new ApplicationDirectory.Default(
      applicationName = "test-app",
      filesystem = filesystem
    ) {
      override def pullFile[T](file: String)(implicit ec: ExecutionContext, um: ByteString => T): Future[T] =
        Future.failed(new RuntimeException("test failure"))
    }

    for {
      base <- Base(modeArguments = modeArguments, applicationDirectory = directory)
      init <- Init(base, console = None)
      e <- Credentials(base, init).failed.map { case NonFatal(e: ServiceStartupFailure) => e }
    } yield {
      e.cause should be("file")
      e.message should include("test failure")
    }
  }

  it should "handle device secret decryption failures" in {
    val modeArguments = ApplicationArguments.Mode.Maintenance(
      regenerateApiCertificate = false,
      deviceSecretOperation = None,
      userCredentialsOperation = Some(ApplicationArguments.Mode.Maintenance.UserCredentialsOperation.Reset),
      currentUserName = currentUserName,
      currentUserPassword = currentUserPassword,
      newUserPassword = newUserPassword,
      newUserSalt = newUserSalt
    )

    val directory = createCustomApplicationDirectory(
      deviceSecret = Some(ByteString.fromString("invalid-device-key"))
    )

    for {
      base <- Base(modeArguments = modeArguments, applicationDirectory = directory)
      init <- Init(base, console = None)
      e <- Credentials(base, init).failed.map { case NonFatal(e: ServiceStartupFailure) => e }
    } yield {
      e.cause should be("credentials")
      e.message should include("Tag mismatch")
    }
  }

  it should "skip credentials operations if none are requested" in {
    val modeArguments = ApplicationArguments.Mode.Maintenance(
      regenerateApiCertificate = false,
      deviceSecretOperation = None,
      userCredentialsOperation = None,
      currentUserName = currentUserName,
      currentUserPassword = currentUserPassword,
      newUserPassword = newUserPassword,
      newUserSalt = newUserSalt
    )

    val directory = createCustomApplicationDirectory(
      deviceSecret = Some(currentEncryptedDeviceSecret)
    )

    for {
      base <- Base(modeArguments = modeArguments, applicationDirectory = directory)
      init <- Init(base, console = None)
      credentials <- Credentials(base, init)
      existingUserSalt = ConfigOverride.load(directory).getString(userSaltConfigEntry)
      existingDeviceSecret <- directory.pullFile[ByteString](Files.DeviceSecret)
      _ <- credentials.apply()
      updatedUserSalt = ConfigOverride.load(directory).getString(userSaltConfigEntry)
      updatedDeviceSecret <- directory.pullFile[ByteString](Files.DeviceSecret)
    } yield {
      existingUserSalt should be(currentUserSalt)
      existingDeviceSecret should be(currentEncryptedDeviceSecret)
      updatedUserSalt should be(currentUserSalt)
      updatedDeviceSecret should be(currentEncryptedDeviceSecret)
    }
  }

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "MaintenanceCredentialsSpec"
  )

  private def createCustomApplicationDirectory(
    deviceSecret: Option[ByteString]
  ): ApplicationDirectory =
    createApplicationDirectory(
      init = dir => {
        val path = dir.config.get
        java.nio.file.Files.createDirectories(path)

        java.nio.file.Files.writeString(
          path.resolve(Files.ConfigOverride),
          s"""{$userSaltConfigEntry: "$currentUserSalt"}"""
        )

        deviceSecret.foreach { secret =>
          java.nio.file.Files.write(path.resolve(Files.DeviceSecret), secret.toArray)
        }
      }
    )

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private val currentEncryptedDeviceSecret = "08ko0LWDalPvEHna/WWuq3LoEaA3m4dQ1QuP".decodeFromBase64 // decrypted == "test-secret"
  private val reEncryptedDeviceSecret = "hU9e2iNzu8H3G5e4kmBMi4hMG3Y9ZCl2oYGG".decodeFromBase64 // decrypted == "test-secret"

  private val currentUserSalt = "test-salt"
  private val currentUserName = "test-user"
  private val currentUserPassword = "test-password".toCharArray
  private val newUserPassword = "new-password".toCharArray
  private val newUserSalt = "new-salt"

  private val userSaltConfigEntry = "stasis.client.server.api.user-salt"

  override implicit val timeout: Timeout = 5.seconds
}
