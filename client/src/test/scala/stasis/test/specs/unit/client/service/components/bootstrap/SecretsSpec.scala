package stasis.test.specs.unit.client.service.components.bootstrap

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
import stasis.client.service.components.bootstrap.Base
import stasis.client.service.components.bootstrap.Init
import stasis.client.service.components.bootstrap.Secrets
import stasis.client.service.components.exceptions.ServiceStartupFailure
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.EncodingHelpers
import stasis.test.specs.unit.client.ResourceHelpers
import stasis.test.specs.unit.client.mocks.MockServerApiEndpoint
import stasis.test.specs.unit.client.mocks.MockTokenEndpoint
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class SecretsSpec extends AsyncUnitSpec with ResourceHelpers with EncodingHelpers {
  "A Secrets component" should "supporting retrieving existing device secrets from the server" in {
    val tokenEndpointPort = ports.dequeue()
    val tokenEndpoint = MockTokenEndpoint(port = tokenEndpointPort, token = apiCredentials.token)
    tokenEndpoint.start()

    val apiEndpointPort = ports.dequeue()
    val apiEndpoint = new MockServerApiEndpoint(
      expectedCredentials = apiCredentials,
      expectedDeviceKey = Some(remoteEncryptedDeviceSecret)
    ).start(port = apiEndpointPort)

    val modeArguments = ApplicationArguments.Mode.Bootstrap(
      serverBootstrapUrl = s"https://test-url",
      bootstrapCode = "test-code",
      acceptSelfSignedCertificates = true,
      userName = userName,
      userPassword = userPassword,
      userPasswordConfirm = userPassword
    )

    val directory = createCustomApplicationDirectory(
      tokenEndpointPort = tokenEndpointPort,
      apiEndpointPort = apiEndpointPort
    )

    for {
      base <- Base(modeArguments = modeArguments, applicationDirectory = directory)
      init <- Init(base, console = None)
      secrets <- Secrets(base, init)
      _ <- secrets.create()
      deviceSecret <- directory.pullFile[ByteString](Files.DeviceSecret)
      _ <- apiEndpoint.flatMap(_.terminate(100.millis))
    } yield {
      tokenEndpoint.stop()
      deviceSecret should be(localEncryptedDeviceSecret)
    }
  }

  it should "support creating new device secrets" in {
    val tokenEndpointPort = ports.dequeue()
    val tokenEndpoint = MockTokenEndpoint(port = tokenEndpointPort, token = apiCredentials.token)
    tokenEndpoint.start()

    val apiEndpointPort = ports.dequeue()
    val apiEndpoint = new MockServerApiEndpoint(expectedCredentials = apiCredentials).start(port = apiEndpointPort)

    val modeArguments = ApplicationArguments.Mode.Bootstrap(
      serverBootstrapUrl = s"https://test-url",
      bootstrapCode = "test-code",
      acceptSelfSignedCertificates = true,
      userName = userName,
      userPassword = userPassword,
      userPasswordConfirm = userPassword
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
      _ <- secrets.create()
      deviceSecret <- directory.pullFile[ByteString](Files.DeviceSecret)
      _ <- apiEndpoint.flatMap(_.terminate(100.millis))
    } yield {
      tokenEndpoint.stop()
      deviceSecret should not be localEncryptedDeviceSecret
      deviceSecret.size should be >= Secrets.DefaultDeviceSecretSize
    }
  }

  it should "keep existing local device secret" in {
    val modeArguments = ApplicationArguments.Mode.Bootstrap(
      serverBootstrapUrl = s"https://test-url",
      bootstrapCode = "test-code",
      acceptSelfSignedCertificates = true,
      userName = userName,
      userPassword = userPassword,
      userPasswordConfirm = userPassword
    )

    val directory = createCustomApplicationDirectory(
      tokenEndpointPort = 9090,
      apiEndpointPort = 9091,
      deviceSecret = Some(localEncryptedDeviceSecret)
    )

    for {
      base <- Base(modeArguments = modeArguments, applicationDirectory = directory)
      init <- Init(base, console = None)
      secrets <- Secrets(base, init)
      _ <- secrets.create()
      deviceSecret <- directory.pullFile[ByteString](Files.DeviceSecret)
    } yield {
      deviceSecret should be(localEncryptedDeviceSecret)
    }
  }

  it should "handle credentials retrieval failures" in {
    val modeArguments = ApplicationArguments.Mode.Bootstrap(
      serverBootstrapUrl = s"https://test-url",
      bootstrapCode = "test-code",
      acceptSelfSignedCertificates = true,
      userName = userName,
      userPassword = userPassword,
      userPasswordConfirm = userPassword
    )

    val directory = createCustomApplicationDirectory()

    Secrets(
      base = Base(modeArguments = modeArguments, applicationDirectory = directory).await,
      init = new Init {
        override def arguments(): Future[ApplicationArguments.Mode.Bootstrap] = Future.successful(modeArguments)
        override def credentials(): Future[(String, Array[Char])] = Future.failed(new RuntimeException("test failure"))
      }
    ).await
      .create()
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e: ServiceStartupFailure) =>
        e.cause should be("credentials")
        e.message should be("RuntimeException: test failure")
      }
  }

  it should "handle config override file retrieval failures" in {
    val modeArguments = ApplicationArguments.Mode.Bootstrap(
      serverBootstrapUrl = s"https://test-url",
      bootstrapCode = "test-code",
      acceptSelfSignedCertificates = true,
      userName = userName,
      userPassword = userPassword,
      userPasswordConfirm = userPassword
    )

    Secrets(
      base = Base(
        modeArguments = modeArguments,
        applicationDirectory = createApplicationDirectory(init = _ => ())
      ).await,
      init = new Init {
        override def arguments(): Future[ApplicationArguments.Mode.Bootstrap] = Future.successful(modeArguments)
        override def credentials(): Future[(String, Array[Char])] = Future.successful((userName, userPassword))
      }
    ).await
      .create()
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e: ServiceStartupFailure) =>
        e.cause should be("config")
        e.message should startWith("FileNotFoundException: File [client.conf] not found")
      }
  }

  it should "handle device secret file creation failures" in {
    val tokenEndpointPort = ports.dequeue()
    val tokenEndpoint = MockTokenEndpoint(port = tokenEndpointPort, token = apiCredentials.token)
    tokenEndpoint.start()

    val apiEndpointPort = ports.dequeue()
    val apiEndpoint = new MockServerApiEndpoint(expectedCredentials = apiCredentials).start(port = apiEndpointPort)

    val modeArguments = ApplicationArguments.Mode.Bootstrap(
      serverBootstrapUrl = s"https://test-url",
      bootstrapCode = "test-code",
      acceptSelfSignedCertificates = true,
      userName = userName,
      userPassword = userPassword,
      userPasswordConfirm = userPassword
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
        override def arguments(): Future[ApplicationArguments.Mode.Bootstrap] = Future.successful(modeArguments)
        override def credentials(): Future[(String, Array[Char])] = Future.successful((userName, userPassword))
      }
    ).await
      .create()
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

  it should "handle core token retrieval failures" in {
    val tokenEndpointPort = ports.dequeue()
    val allowedGrants = Seq("password") // API token retrieval IS allowed; core token retrieval NOT allowed
    val endpoint = MockTokenEndpoint(port = tokenEndpointPort, token = "test-token", allowedGrants = allowedGrants)
    endpoint.start()

    val modeArguments = ApplicationArguments.Mode.Bootstrap(
      serverBootstrapUrl = s"https://test-url",
      bootstrapCode = "test-code",
      acceptSelfSignedCertificates = true,
      userName = userName,
      userPassword = userPassword,
      userPasswordConfirm = userPassword
    )

    val directory = createCustomApplicationDirectory(
      tokenEndpointPort = tokenEndpointPort,
      apiEndpointPort = 9091
    )

    Secrets(
      base = Base(modeArguments = modeArguments, applicationDirectory = directory).await,
      init = new Init {
        override def arguments(): Future[ApplicationArguments.Mode.Bootstrap] = Future.successful(modeArguments)
        override def credentials(): Future[(String, Array[Char])] = Future.successful((userName, userPassword))
      }
    ).await
      .create()
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e: ServiceStartupFailure) =>
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

    val modeArguments = ApplicationArguments.Mode.Bootstrap(
      serverBootstrapUrl = s"https://test-url",
      bootstrapCode = "test-code",
      acceptSelfSignedCertificates = true,
      userName = userName,
      userPassword = userPassword,
      userPasswordConfirm = userPassword
    )

    val directory = createCustomApplicationDirectory(
      tokenEndpointPort = tokenEndpointPort,
      apiEndpointPort = 9091
    )

    Secrets(
      base = Base(modeArguments = modeArguments, applicationDirectory = directory).await,
      init = new Init {
        override def arguments(): Future[ApplicationArguments.Mode.Bootstrap] = Future.successful(modeArguments)
        override def credentials(): Future[(String, Array[Char])] = Future.successful((userName, userPassword))
      }
    ).await
      .create()
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e: ServiceStartupFailure) =>
        endpoint.stop()

        e.cause should be("token")
        e.message should include("400 Bad Request")
      }
  }

  it should "handle key retrieval failures" in {
    val tokenEndpointPort = ports.dequeue()
    val tokenEndpoint = MockTokenEndpoint(port = tokenEndpointPort, token = apiCredentials.token)
    tokenEndpoint.start()

    val modeArguments = ApplicationArguments.Mode.Bootstrap(
      serverBootstrapUrl = s"https://test-url",
      bootstrapCode = "test-code",
      acceptSelfSignedCertificates = true,
      userName = userName,
      userPassword = userPassword,
      userPasswordConfirm = userPassword
    )

    val directory = createCustomApplicationDirectory(
      tokenEndpointPort = tokenEndpointPort,
      apiEndpointPort = 9999
    )

    Secrets(
      base = Base(modeArguments = modeArguments, applicationDirectory = directory).await,
      init = new Init {
        override def arguments(): Future[ApplicationArguments.Mode.Bootstrap] = Future.successful(modeArguments)
        override def credentials(): Future[(String, Array[Char])] = Future.successful((userName, userPassword))
      }
    ).await
      .create()
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e: ServiceStartupFailure) =>
        tokenEndpoint.stop()

        e.cause should be("api")
        e.message should include("Connection refused")
      }
  }

  it should "skip creating device secret if one already exists" in {
    val modeArguments = ApplicationArguments.Mode.Bootstrap(
      serverBootstrapUrl = s"https://test-url",
      bootstrapCode = "test-code",
      acceptSelfSignedCertificates = true,
      userName = userName,
      userPassword = userPassword,
      userPasswordConfirm = userPassword
    )

    val directory = createCustomApplicationDirectory(
      tokenEndpointPort = 9090,
      apiEndpointPort = 9091,
      deviceSecret = Some(localEncryptedDeviceSecret)
    )

    for {
      base <- Base(modeArguments = modeArguments, applicationDirectory = directory)
      init <- Init(base, console = None)
      secrets <- Secrets(base, init)
      _ <- secrets.create()
      deviceSecret <- directory.pullFile[ByteString](Files.DeviceSecret)
    } yield {
      deviceSecret should be(localEncryptedDeviceSecret)
    }
  }

  private def createCustomApplicationDirectory(): ApplicationDirectory =
    createCustomApplicationDirectory(tokenEndpointPort = 9090, apiEndpointPort = 9091, deviceSecret = None)

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
    "BootstrapSecretsSpec"
  )

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private val ports: mutable.Queue[Int] = (39000 to 39100).to(mutable.Queue)

  private val remoteEncryptedDeviceSecret = "trHMvaUulBOLG7NlWGryUHEeGA0IxkE39pEG".decodeFromBase64 // decrypted == "test-secret"
  private val localEncryptedDeviceSecret = "08ko0LWDalPvEHna/WWuq3LoEaA3m4dQ1QuP".decodeFromBase64 // decrypted == "test-secret"

  private val userName = "test-user"
  private val userPassword = "test-password".toCharArray

  private implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

  private val apiCredentials = OAuth2BearerToken("test-token")

  private val apiEndpointConfigEntry = "stasis.client.server.api.url"
  private val tokenEndpointConfigEntry = "stasis.client.server.authentication.token-endpoint"

  override implicit val timeout: Timeout = 5.seconds
}
