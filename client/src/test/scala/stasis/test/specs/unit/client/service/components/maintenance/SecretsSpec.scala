package stasis.test.specs.unit.client.service.components.maintenance

import java.nio.file.Path
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal
import com.google.common.jimfs.Jimfs
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.SpawnProtocol
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken
import org.apache.pekko.util.{ByteString, Timeout}
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import stasis.client.service.ApplicationArguments
import stasis.client.service.ApplicationDirectory
import stasis.client.service.components.Files
import stasis.client.service.components.exceptions.ServiceStartupFailure
import stasis.client.service.components.maintenance.Base
import stasis.client.service.components.maintenance.Init
import stasis.client.service.components.maintenance.Secrets
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.EncodingHelpers
import stasis.test.specs.unit.client.ResourceHelpers
import stasis.test.specs.unit.client.mocks.MockServerApiEndpoint
import stasis.test.specs.unit.client.mocks.MockTokenEndpoint
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class SecretsSpec extends AsyncUnitSpec with ResourceHelpers with EncodingHelpers {
  "A Secrets component" should "push device secrets to server API" in {
    val tokenEndpointPort = ports.dequeue()
    val tokenEndpoint = MockTokenEndpoint(port = tokenEndpointPort, token = apiCredentials.token)
    tokenEndpoint.start()

    val apiEndpointPort = ports.dequeue()
    val apiEndpoint = new MockServerApiEndpoint(
      expectedCredentials = apiCredentials,
      expectedDeviceKey = Some(remoteEncryptedDeviceSecret)
    )

    val apiEndpointBinding = apiEndpoint.start(port = apiEndpointPort)

    val modeArguments = ApplicationArguments.Mode.Maintenance(
      regenerateApiCertificate = false,
      deviceSecretOperation = Some(ApplicationArguments.Mode.Maintenance.DeviceSecretOperation.Push),
      userCredentialsOperation = None,
      currentUserName = userName,
      currentUserPassword = userPassword,
      newUserPassword = Array.emptyCharArray,
      newUserSalt = ""
    )

    val directory = createCustomApplicationDirectory(
      tokenEndpointPort = tokenEndpointPort,
      apiEndpointPort = apiEndpointPort,
      deviceSecret = Some(localEncryptedDeviceSecret)
    )

    for {
      base <- Base(modeArguments = modeArguments, applicationDirectory = directory)
      init <- Init(base, console = None)
      secrets <- Secrets(base, init)
      deviceKeyExistsBefore <- apiEndpoint.deviceKeyExists(deviceId)
      _ <- secrets.apply()
      deviceKeyExistsAfter <- apiEndpoint.deviceKeyExists(deviceId)
      _ <- apiEndpointBinding.flatMap(_.terminate(100.millis))
    } yield {
      tokenEndpoint.stop()
      deviceKeyExistsBefore should be(false)
      deviceKeyExistsAfter should be(true)
    }
  }

  it should "pull device secrets from server API" in {
    val tokenEndpointPort = ports.dequeue()
    val tokenEndpoint = MockTokenEndpoint(port = tokenEndpointPort, token = apiCredentials.token)
    tokenEndpoint.start()

    val apiEndpointPort = ports.dequeue()
    val apiEndpoint = new MockServerApiEndpoint(
      expectedCredentials = apiCredentials,
      expectedDeviceKey = Some(remoteEncryptedDeviceSecret)
    )

    val apiEndpointBinding = apiEndpoint.start(port = apiEndpointPort)

    val modeArguments = ApplicationArguments.Mode.Maintenance(
      regenerateApiCertificate = false,
      deviceSecretOperation = Some(ApplicationArguments.Mode.Maintenance.DeviceSecretOperation.Pull),
      userCredentialsOperation = None,
      currentUserName = userName,
      currentUserPassword = userPassword,
      newUserPassword = Array.emptyCharArray,
      newUserSalt = ""
    )

    val directory = createCustomApplicationDirectory(
      tokenEndpointPort = tokenEndpointPort,
      apiEndpointPort = apiEndpointPort,
      deviceSecret = None
    )

    for {
      base <- Base(modeArguments = modeArguments, applicationDirectory = directory)
      init <- Init(base, console = None)
      secrets <- Secrets(base, init)
      missingDeviceSecret <- directory.pullFile[ByteString](Files.DeviceSecret).failed
      _ <- secrets.apply()
      _ <- apiEndpointBinding.flatMap(_.terminate(100.millis))
      existingDeviceSecret <- directory.pullFile[ByteString](Files.DeviceSecret)
    } yield {
      tokenEndpoint.stop()
      missingDeviceSecret.getMessage should startWith(s"File [${Files.DeviceSecret}] not found")
      existingDeviceSecret should be(localEncryptedDeviceSecret)
    }
  }

  it should "handle credentials retrieval failures" in {
    val modeArguments = ApplicationArguments.Mode.Maintenance(
      regenerateApiCertificate = false,
      deviceSecretOperation = Some(ApplicationArguments.Mode.Maintenance.DeviceSecretOperation.Push),
      userCredentialsOperation = None,
      currentUserName = userName,
      currentUserPassword = userPassword,
      newUserPassword = Array.emptyCharArray,
      newUserSalt = ""
    )

    val directory = createCustomApplicationDirectory(apiEndpointPort = 9090, tokenEndpointPort = 9091)

    Secrets(
      base = Base(modeArguments = modeArguments, applicationDirectory = directory).await,
      init = new Init {
        override def currentCredentials(): Future[(String, Array[Char])] = Future.failed(new RuntimeException("test failure"))
        override def newCredentials(): Future[(Array[Char], String)] = Future.failed(new RuntimeException("test failure"))
      }
    ).map { result =>
      fail(s"Unexpected result received: [$result]")
    }.recover { case NonFatal(e: ServiceStartupFailure) =>
      e.cause should be("credentials")
      e.message should be("RuntimeException: test failure")
    }
  }

  it should "handle local device secret decryption failures" in {
    val tokenEndpointPort = ports.dequeue()
    val tokenEndpoint = MockTokenEndpoint(port = tokenEndpointPort, token = apiCredentials.token)
    tokenEndpoint.start()

    val modeArguments = ApplicationArguments.Mode.Maintenance(
      regenerateApiCertificate = false,
      deviceSecretOperation = Some(ApplicationArguments.Mode.Maintenance.DeviceSecretOperation.Pull),
      userCredentialsOperation = None,
      currentUserName = userName,
      currentUserPassword = userPassword,
      newUserPassword = Array.emptyCharArray,
      newUserSalt = ""
    )

    val directory = createCustomApplicationDirectory(
      tokenEndpointPort = tokenEndpointPort,
      apiEndpointPort = 9092,
      deviceSecret = Some(ByteString.fromString("invalid-device-key"))
    )

    for {
      base <- Base(modeArguments = modeArguments, applicationDirectory = directory)
      init <- Init(base, console = None)
      e <- Secrets(base, init).failed.map { case NonFatal(e: ServiceStartupFailure) => e }
    } yield {
      tokenEndpoint.stop()
      e.cause should be("credentials")
      e.message should include("Tag mismatch")
    }
  }

  it should "handle core token retrieval failures" in {
    val tokenEndpointPort = ports.dequeue()
    val allowedGrants = Seq("password") // API token retrieval IS allowed; core token retrieval NOT allowed
    val endpoint = MockTokenEndpoint(port = tokenEndpointPort, token = "test-token", allowedGrants = allowedGrants)
    endpoint.start()

    val modeArguments = ApplicationArguments.Mode.Maintenance(
      regenerateApiCertificate = false,
      deviceSecretOperation = Some(ApplicationArguments.Mode.Maintenance.DeviceSecretOperation.Pull),
      userCredentialsOperation = None,
      currentUserName = userName,
      currentUserPassword = userPassword,
      newUserPassword = Array.emptyCharArray,
      newUserSalt = ""
    )

    val directory = createCustomApplicationDirectory(
      tokenEndpointPort = tokenEndpointPort,
      apiEndpointPort = 9093
    )

    Secrets(
      base = Base(modeArguments = modeArguments, applicationDirectory = directory).await,
      init = new Init {
        override def currentCredentials(): Future[(String, Array[Char])] = Future.successful((userName, userPassword))
        override def newCredentials(): Future[(Array[Char], String)] = Future.failed(new RuntimeException("test failure"))
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

    val modeArguments = ApplicationArguments.Mode.Maintenance(
      regenerateApiCertificate = false,
      deviceSecretOperation = Some(ApplicationArguments.Mode.Maintenance.DeviceSecretOperation.Pull),
      userCredentialsOperation = None,
      currentUserName = userName,
      currentUserPassword = userPassword,
      newUserPassword = Array.emptyCharArray,
      newUserSalt = ""
    )

    val directory = createCustomApplicationDirectory(
      tokenEndpointPort = tokenEndpointPort,
      apiEndpointPort = 9094
    )

    Secrets(
      base = Base(modeArguments = modeArguments, applicationDirectory = directory).await,
      init = new Init {
        override def currentCredentials(): Future[(String, Array[Char])] = Future.successful((userName, userPassword))
        override def newCredentials(): Future[(Array[Char], String)] = Future.failed(new RuntimeException("test failure"))
      }
    ).map { result =>
      fail(s"Unexpected result received: [$result]")
    }.recover { case NonFatal(e: ServiceStartupFailure) =>
      endpoint.stop()

      e.cause should be("token")
      e.message should include("400 Bad Request")
    }
  }

  it should "handle failures when pulling device secret" in {
    val tokenEndpointPort = ports.dequeue()
    val tokenEndpoint = MockTokenEndpoint(port = tokenEndpointPort, token = apiCredentials.token)
    tokenEndpoint.start()

    val modeArguments = ApplicationArguments.Mode.Maintenance(
      regenerateApiCertificate = false,
      deviceSecretOperation = Some(ApplicationArguments.Mode.Maintenance.DeviceSecretOperation.Pull),
      userCredentialsOperation = None,
      currentUserName = userName,
      currentUserPassword = userPassword,
      newUserPassword = Array.emptyCharArray,
      newUserSalt = ""
    )

    val directory = createCustomApplicationDirectory(
      tokenEndpointPort = tokenEndpointPort,
      apiEndpointPort = 9095,
      deviceSecret = None
    )

    for {
      base <- Base(modeArguments = modeArguments, applicationDirectory = directory)
      init <- Init(base, console = None)
      secrets <- Secrets(base, init)
      e <- secrets.apply().failed.map { case NonFatal(e: ServiceStartupFailure) => e }
    } yield {
      tokenEndpoint.stop()
      e.cause should be("api")
      e.message should include("Connection refused")
    }
  }

  it should "handle decryption failures when pulling device secret" in {
    val tokenEndpointPort = ports.dequeue()
    val tokenEndpoint = MockTokenEndpoint(port = tokenEndpointPort, token = apiCredentials.token)
    tokenEndpoint.start()

    val apiEndpointPort = ports.dequeue()
    val apiEndpoint = new MockServerApiEndpoint(
      expectedCredentials = apiCredentials,
      expectedDeviceKey = Some(ByteString.fromString("invalid-device-key"))
    )

    val apiEndpointBinding = apiEndpoint.start(port = apiEndpointPort)

    val modeArguments = ApplicationArguments.Mode.Maintenance(
      regenerateApiCertificate = false,
      deviceSecretOperation = Some(ApplicationArguments.Mode.Maintenance.DeviceSecretOperation.Pull),
      userCredentialsOperation = None,
      currentUserName = userName,
      currentUserPassword = userPassword,
      newUserPassword = Array.emptyCharArray,
      newUserSalt = ""
    )

    val directory = createCustomApplicationDirectory(
      tokenEndpointPort = tokenEndpointPort,
      apiEndpointPort = apiEndpointPort,
      deviceSecret = None
    )

    for {
      base <- Base(modeArguments = modeArguments, applicationDirectory = directory)
      init <- Init(base, console = None)
      secrets <- Secrets(base, init)
      e <- secrets.apply().failed.map { case NonFatal(e: ServiceStartupFailure) => e }
      _ <- apiEndpointBinding.flatMap(_.terminate(100.millis))
      missingDeviceSecret <- directory.pullFile[ByteString](Files.DeviceSecret).failed
    } yield {
      tokenEndpoint.stop()
      e.cause should be("credentials")
      e.message should include("Tag mismatch")
      missingDeviceSecret.getMessage should startWith(s"File [${Files.DeviceSecret}] not found")
    }
  }

  it should "handle failures when loading device secret from file" in {
    val modeArguments = ApplicationArguments.Mode.Maintenance(
      regenerateApiCertificate = false,
      deviceSecretOperation = Some(ApplicationArguments.Mode.Maintenance.DeviceSecretOperation.Push),
      userCredentialsOperation = None,
      currentUserName = userName,
      currentUserPassword = userPassword,
      newUserPassword = Array.emptyCharArray,
      newUserSalt = ""
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
      e <- Secrets(base, init).failed.map { case NonFatal(e: ServiceStartupFailure) => e }
    } yield {
      e.cause should be("file")
      e.message should include("test failure")
    }
  }

  it should "handle failures when pushing device secret" in {
    val tokenEndpointPort = ports.dequeue()
    val tokenEndpoint = MockTokenEndpoint(port = tokenEndpointPort, token = apiCredentials.token)
    tokenEndpoint.start()

    val modeArguments = ApplicationArguments.Mode.Maintenance(
      regenerateApiCertificate = false,
      deviceSecretOperation = Some(ApplicationArguments.Mode.Maintenance.DeviceSecretOperation.Push),
      userCredentialsOperation = None,
      currentUserName = userName,
      currentUserPassword = userPassword,
      newUserPassword = Array.emptyCharArray,
      newUserSalt = ""
    )

    val directory = createCustomApplicationDirectory(
      tokenEndpointPort = tokenEndpointPort,
      apiEndpointPort = 9096,
      deviceSecret = Some(localEncryptedDeviceSecret)
    )

    for {
      base <- Base(modeArguments = modeArguments, applicationDirectory = directory)
      init <- Init(base, console = None)
      secrets <- Secrets(base, init)
      e <- secrets.apply().failed.map { case NonFatal(e: ServiceStartupFailure) => e }
    } yield {
      tokenEndpoint.stop()
      e.cause should be("api")
      e.message should include("Connection refused")
    }
  }

  it should "handle missing local device secrets during push" in {
    val tokenEndpointPort = ports.dequeue()
    val tokenEndpoint = MockTokenEndpoint(port = tokenEndpointPort, token = apiCredentials.token)
    tokenEndpoint.start()

    val modeArguments = ApplicationArguments.Mode.Maintenance(
      regenerateApiCertificate = false,
      deviceSecretOperation = Some(ApplicationArguments.Mode.Maintenance.DeviceSecretOperation.Push),
      userCredentialsOperation = None,
      currentUserName = userName,
      currentUserPassword = userPassword,
      newUserPassword = Array.emptyCharArray,
      newUserSalt = ""
    )

    val directory = createCustomApplicationDirectory(
      tokenEndpointPort = tokenEndpointPort,
      apiEndpointPort = 9097,
      deviceSecret = None
    )

    for {
      base <- Base(modeArguments = modeArguments, applicationDirectory = directory)
      init <- Init(base, console = None)
      secrets <- Secrets(base, init)
      e <- secrets.apply().failed.map { case NonFatal(e: ServiceStartupFailure) => e }
    } yield {
      tokenEndpoint.stop()
      e.cause should be("credentials")
      e.message should include("Failed to push device secret; no secret was found locally")
    }
  }

  it should "handle missing remote device secrets during pull" in {
    val tokenEndpointPort = ports.dequeue()
    val tokenEndpoint = MockTokenEndpoint(port = tokenEndpointPort, token = apiCredentials.token)
    tokenEndpoint.start()

    val apiEndpointPort = ports.dequeue()
    val apiEndpoint = new MockServerApiEndpoint(
      expectedCredentials = apiCredentials,
      expectedDeviceKey = None
    )

    val apiEndpointBinding = apiEndpoint.start(port = apiEndpointPort)

    val modeArguments = ApplicationArguments.Mode.Maintenance(
      regenerateApiCertificate = false,
      deviceSecretOperation = Some(ApplicationArguments.Mode.Maintenance.DeviceSecretOperation.Pull),
      userCredentialsOperation = None,
      currentUserName = userName,
      currentUserPassword = userPassword,
      newUserPassword = Array.emptyCharArray,
      newUserSalt = ""
    )

    val directory = createCustomApplicationDirectory(
      tokenEndpointPort = tokenEndpointPort,
      apiEndpointPort = apiEndpointPort,
      deviceSecret = None
    )

    for {
      base <- Base(modeArguments = modeArguments, applicationDirectory = directory)
      init <- Init(base, console = None)
      secrets <- Secrets(base, init)
      e <- secrets.apply().failed.map { case NonFatal(e: ServiceStartupFailure) => e }
      _ <- apiEndpointBinding.flatMap(_.terminate(100.millis))
      missingDeviceSecret <- directory.pullFile[ByteString](Files.DeviceSecret).failed
    } yield {
      tokenEndpoint.stop()
      e.cause should be("credentials")
      e.message should include("Failed to pull device secret; no secret was found on server")
      missingDeviceSecret.getMessage should startWith(s"File [${Files.DeviceSecret}] not found")
    }
  }

  it should "handle device secret file creation failures" in {
    val tokenEndpointPort = ports.dequeue()
    val tokenEndpoint = MockTokenEndpoint(port = tokenEndpointPort, token = apiCredentials.token)
    tokenEndpoint.start()

    val apiEndpointPort = ports.dequeue()
    val apiEndpoint = new MockServerApiEndpoint(
      expectedCredentials = apiCredentials,
      expectedDeviceKey = Some(remoteEncryptedDeviceSecret)
    ).start(port = apiEndpointPort)

    val modeArguments = ApplicationArguments.Mode.Maintenance(
      regenerateApiCertificate = false,
      deviceSecretOperation = Some(ApplicationArguments.Mode.Maintenance.DeviceSecretOperation.Pull),
      userCredentialsOperation = None,
      currentUserName = userName,
      currentUserPassword = userPassword,
      newUserPassword = Array.emptyCharArray,
      newUserSalt = ""
    )

    val filesystem = Jimfs.newFileSystem()

    val directory = new ApplicationDirectory.Default(
      applicationName = "test-app",
      filesystem = filesystem
    ) {
      override def pushFile[T](file: String, content: T)(implicit ec: ExecutionContext, m: T => ByteString): Future[Path] =
        Future.failed(new RuntimeException("test failure"))
    }

    val path = directory.config.get
    java.nio.file.Files.createDirectories(path)
    java.nio.file.Files.writeString(
      path.resolve(Files.ConfigOverride),
      s"""{
         |$tokenEndpointConfigEntry: "http://localhost:$tokenEndpointPort/oauth/token",
         |$apiEndpointConfigEntry: "http://localhost:$apiEndpointPort"
             }""".stripMargin.replaceAll("\n", "")
    )

    val result = Secrets(
      base = Base(modeArguments = modeArguments, applicationDirectory = directory).await,
      init = new Init {
        override def currentCredentials(): Future[(String, Array[Char])] = Future.successful((userName, userPassword))
        override def newCredentials(): Future[(Array[Char], String)] = Future.failed(new RuntimeException("test failure"))
      }
    ).await
      .apply()
      .failed

    for {
      e <- result.collect { case NonFatal(e: ServiceStartupFailure) => e }
      _ <- apiEndpoint.flatMap(_.terminate(100.millis))
    } yield {
      tokenEndpoint.stop()
      e.cause should be("file")
      e.message should startWith("RuntimeException: test failure")
    }
  }

  it should "skip secrets operations if none are requested" in {
    val modeArguments = ApplicationArguments.Mode.Maintenance(
      regenerateApiCertificate = false,
      deviceSecretOperation = None,
      userCredentialsOperation = None,
      currentUserName = userName,
      currentUserPassword = userPassword,
      newUserPassword = Array.emptyCharArray,
      newUserSalt = ""
    )

    val directory = createCustomApplicationDirectory(apiEndpointPort = 9098, tokenEndpointPort = 9099)

    for {
      base <- Base(modeArguments = modeArguments, applicationDirectory = directory)
      init <- Init(base, console = None)
      secrets <- Secrets(base, init)
      _ <- secrets.apply()
      missingDeviceSecret <- directory.pullFile[ByteString](Files.DeviceSecret).failed
    } yield {
      missingDeviceSecret.getMessage should startWith(s"File [${Files.DeviceSecret}] not found")
    }
  }

  private def createCustomApplicationDirectory(tokenEndpointPort: Int, apiEndpointPort: Int): ApplicationDirectory =
    createCustomApplicationDirectory(
      tokenEndpointPort = tokenEndpointPort,
      apiEndpointPort = apiEndpointPort,
      deviceSecret = None
    )

  private def createCustomApplicationDirectory(
    apiEndpointPort: Int,
    tokenEndpointPort: Int,
    deviceSecret: Option[ByteString]
  ): ApplicationDirectory =
    createApplicationDirectory(
      init = dir => {
        val path = dir.config.get
        java.nio.file.Files.createDirectories(path)

        java.nio.file.Files.writeString(
          path.resolve(Files.ConfigOverride),
          s"""{
             |$tokenEndpointConfigEntry: "http://localhost:$tokenEndpointPort/oauth/token",
             |$apiEndpointConfigEntry: "http://localhost:$apiEndpointPort"
             }""".stripMargin.replaceAll("\n", "")
        )

        deviceSecret.foreach { secret =>
          java.nio.file.Files.write(path.resolve(Files.DeviceSecret), secret.toArray)
        }
      }
    )

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "MaintenanceSecretsSpec"
  )

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private val ports: mutable.Queue[Int] = (40000 to 40100).to(mutable.Queue)

  private val remoteEncryptedDeviceSecret = "trHMvaUulBOLG7NlWGryUHEeGA0IxkE39pEG".decodeFromBase64 // decrypted == "test-secret"
  private val localEncryptedDeviceSecret = "08ko0LWDalPvEHna/WWuq3LoEaA3m4dQ1QuP".decodeFromBase64 // decrypted == "test-secret"

  private val deviceId = java.util.UUID.fromString("bc3b2b9a-3d04-4c8c-a6bb-b4ee428d1a99")
  private val userName = "test-user"
  private val userPassword = "test-password".toCharArray

  private implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

  private val apiCredentials = OAuth2BearerToken("test-token")

  private val apiEndpointConfigEntry = "stasis.client.server.api.url"
  private val tokenEndpointConfigEntry = "stasis.client.server.authentication.token-endpoint"

  override implicit val timeout: Timeout = 5.seconds
}
