package stasis.test.specs.unit.client.service

import java.io.Console
import java.util.concurrent.ThreadLocalRandom
import akka.Done
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, StatusCodes}
import akka.util.ByteString
import com.typesafe.config.Config
import org.mockito.scalatest.AsyncMockitoSugar
import org.scalatest.Assertion
import org.scalatest.concurrent.Eventually
import play.api.libs.json.Json
import stasis.client.service.components.Files
import stasis.client.service.components.exceptions.ServiceStartupFailure
import stasis.client.service.{ApplicationArguments, ApplicationDirectory, ApplicationTray, Service}
import stasis.core.routing.Node
import stasis.core.security.tls.EndpointContext
import stasis.shared.model.devices.{Device, DeviceBootstrapParameters}
import stasis.shared.model.users.User
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.mocks.{MockServerBootstrapEndpoint, MockTokenEndpoint}
import stasis.test.specs.unit.client.{EncodingHelpers, Fixtures, ResourceHelpers}

import java.util.UUID
import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random
import scala.util.control.NonFatal

class ServiceSpec extends AsyncUnitSpec with ResourceHelpers with EncodingHelpers with Eventually with AsyncMockitoSugar {
  "A Service" should "handle API endpoint requests" in {
    val apiTerminationDelay = 100.millis

    val tokenEndpointPort = ports.dequeue()
    val apiEndpointPort = ports.dequeue()
    val apiInitPort = ports.dequeue()

    val tokenEndpoint = MockTokenEndpoint(port = tokenEndpointPort, token = "test-token")
    tokenEndpoint.start()

    val directory = createApplicationDirectory(
      init = dir => {
        val path = dir.config.get
        java.nio.file.Files.createDirectories(path)

        java.nio.file.Files.createFile(path.resolve("client.rules"))
        java.nio.file.Files.createFile(path.resolve("client.schedules"))

        java.nio.file.Files.write(
          path.resolve(Files.DeviceSecret),
          encryptedDeviceSecret.toArray
        )

        java.nio.file.Files.writeString(
          path.resolve(Files.ConfigOverride),
          s"""{
             |$apiTerminationDelayEntry: "${apiTerminationDelay.toMillis} millis",
             |$tokenEndpointConfigEntry: "http://localhost:$tokenEndpointPort/oauth/token",
             |$apiEndpointConfigEntry: $apiEndpointPort,
             |$apiInitConfigEntry: $apiInitPort,
             }""".stripMargin.replaceAll("\n", "")
        )
      }
    )

    val service = new Service with TestServiceArguments {
      override protected def applicationDirectory: ApplicationDirectory = directory
      override protected def console: Option[Console] = Some(mockConsole(username, password))
    }

    eventually[Assertion] {
      service.state should be(Service.State.Started)
    }

    tokenEndpoint.stop()

    val endpointToken = java.nio.file.Files.readString(directory.config.get.resolve(Files.ApiToken))
    endpointToken should not be empty

    val successfulPingResponse = Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = s"http://localhost:$apiEndpointPort/service/ping"
        ).addCredentials(credentials = OAuth2BearerToken(token = endpointToken))
      )
      .await

    successfulPingResponse.status should be(StatusCodes.OK)

    val stopResponse = Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.PUT,
          uri = s"http://localhost:$apiEndpointPort/service/stop"
        ).addCredentials(credentials = OAuth2BearerToken(token = endpointToken))
      )
      .await

    stopResponse.status should be(StatusCodes.NoContent)
    await(delay = apiTerminationDelay * 3, withSystem = typedSystem)

    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = s"http://localhost:$apiEndpointPort/service/ping"
        ).addCredentials(credentials = OAuth2BearerToken(token = endpointToken))
      )
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should include("Connection refused")
      }
  }

  it should "support API init if a console/TTY is not available" in {
    val initEndpointPort = typedSystem.settings.config.getInt("stasis.client.api.init.port")

    class ExtendedService extends Service with TestServiceArguments {
      override protected def applicationTray: ApplicationTray = ApplicationTray.NoOp()
      def isConsoleAvailable: Boolean = console.isDefined
    }

    val service = new ExtendedService()
    val request = HttpRequest(method = HttpMethods.GET, uri = s"http://localhost:$initEndpointPort/init")

    if (!service.isConsoleAvailable) {
      eventually[Assertion] {
        Http().singleRequest(request).await.status should be(StatusCodes.OK)
      }

      service.stop()
      succeed
    } else {
      Http()
        .singleRequest(request)
        .map { result =>
          fail(s"Unexpected result received: [$result]")
        }
        .recover { case NonFatal(e) =>
          e.getMessage should include("Connection refused")
        }
    }
  }

  it should "handle startup failures" in {
    val apiInitPort = ports.dequeue()

    val directory = createApplicationDirectory(
      init = dir => {
        val path = dir.config.get
        java.nio.file.Files.createDirectories(path)

        java.nio.file.Files.createFile(path.resolve("client.rules"))
        java.nio.file.Files.createFile(path.resolve("client.schedules"))

        java.nio.file.Files.write(path.resolve(Files.DeviceSecret), encryptedDeviceSecret.toArray)
        java.nio.file.Files.writeString(path.resolve(Files.ConfigOverride), s"""{$apiInitConfigEntry: $apiInitPort}""")
      }
    )

    val service = new Service with TestServiceArguments {
      override protected def applicationDirectory: ApplicationDirectory = directory
      override protected def console: Option[Console] = Some(mockConsole(username, password = "invalid-password"))
      override protected def applicationTray: ApplicationTray = ApplicationTray.NoOp()
    }

    eventually[Assertion] {
      service.state match {
        case Service.State.StartupFailed(e: ServiceStartupFailure) =>
          e.cause should be("credentials")
          e.message should include("Tag mismatch")

        case other =>
          fail(s"Unexpected result received: [$other]")
      }
    }
  }

  it should "handle configuration failures" in {
    val directory = createApplicationDirectory(
      init = dir => {
        val path = dir.config.get
        java.nio.file.Files.createDirectories(path)
        java.nio.file.Files.writeString(
          path.resolve(Files.ConfigOverride),
          """{stasis.client.secrets = "invalid"}"""
        )
      }
    )

    val service = new Service with TestServiceArguments {
      override protected def applicationDirectory: ApplicationDirectory = directory
      override protected def console: Option[Console] = Some(mockConsole(username, password))
      override protected def applicationTray: ApplicationTray = ApplicationTray.NoOp()
    }

    eventually[Assertion] {
      service.state match {
        case Service.State.StartupFailed(e: ServiceStartupFailure) =>
          e.cause should be("config")
          e.message should include("secrets has type STRING")

        case other =>
          fail(s"Unexpected result received: [$other]")
      }
    }
  }

  it should "handle unknown failures" in {
    val directory = createApplicationDirectory(
      init = dir => {
        val path = dir.config.get
        java.nio.file.Files.createDirectories(path)
        java.nio.file.Files.writeString(
          path.resolve(Files.ConfigOverride),
          """{stasis.client.analysis.checksum = "invalid"}"""
        )
      }
    )

    val service = new Service with TestServiceArguments {
      override protected def applicationDirectory: ApplicationDirectory = directory
      override protected def console: Option[Console] = Some(mockConsole(username, password))
      override protected def applicationTray: ApplicationTray = ApplicationTray.NoOp()
    }

    eventually[Assertion] {
      service.state match {
        case Service.State.StartupFailed(e: ServiceStartupFailure) =>
          e.cause should be("unknown")
          e.message should include("MatchError")

        case other =>
          fail(s"Unexpected result received: [$other]")
      }
    }
  }

  it should "support performing device bootstrap" in {
    val directory = createApplicationDirectory(init = dir => java.nio.file.Files.createDirectories(dir.config.get))

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
      userPassword = "test-password".toCharArray,
      userPasswordConfirm = "test-password".toCharArray
    )

    val service = new Service with TestServiceArguments {
      override protected def applicationDirectory: ApplicationDirectory = directory
      override protected def applicationArguments: Future[ApplicationArguments] =
        Future.successful(ApplicationArguments(modeArguments))
      override protected def applicationTray: ApplicationTray = ApplicationTray.NoOp()
      override protected def console: Option[Console] = None
    }

    eventually[Assertion] {
      service.state should be(Service.State.Completed)
    }

    for {
      config <- directory.pullFile[ByteString](Files.ConfigOverride)
      rules <- directory.pullFile[ByteString](Files.Default.ClientRules)
      schedules <- directory.pullFile[ByteString](Files.Default.ClientSchedules)
      deviceSecret <- directory.pullFile[ByteString](Files.DeviceSecret)
    } yield {
      config.nonEmpty should be(true)
      rules.nonEmpty should be(true)
      schedules.nonEmpty should be(false)
      deviceSecret.nonEmpty should be(true)

      directory.findFile(Files.TrustStores.Authentication) should be(None)
      directory.findFile(Files.TrustStores.ServerApi) should be(None)
      directory.findFile(Files.TrustStores.ServerCore) should be(None)
    }
  }

  it should "support performing maintenance" in {
    val directory = createApplicationDirectory(init = dir => java.nio.file.Files.createDirectories(dir.config.get))

    val modeArguments = ApplicationArguments.Mode.Maintenance(
      regenerateApiCertificate = true
    )

    val originalConfig = "stasis.client.api.http.context.keystore.password = \"test-password\""
    val originalKeyStore = ByteString("test-keystore")

    val keyStoreName = s"${Files.KeyStores.ClientApi}.p12"

    implicit val stringToByteString: String => ByteString = ByteString.apply
    implicit val ByteStringToString: ByteString => String = _.utf8String

    directory.pushFile(Files.ConfigOverride, originalConfig)(typedSystem.executionContext, implicitly).await
    directory.pushFile(keyStoreName, originalKeyStore)(typedSystem.executionContext, implicitly).await

    val service = new Service with TestServiceArguments {
      override protected def applicationDirectory: ApplicationDirectory = directory

      override protected def applicationArguments: Future[ApplicationArguments] =
        Future.successful(ApplicationArguments(modeArguments))

      override protected def applicationTray: ApplicationTray = ApplicationTray.NoOp()

      override protected def console: Option[Console] = None
    }

    eventually[Assertion] {
      service.state should be(Service.State.Completed)
    }

    for {
      updatedConfig <- directory.pullFile[String](Files.ConfigOverride)
      updatedKeyStore <- directory.pullFile[ByteString](keyStoreName)
    } yield {
      updatedConfig should not be originalConfig
      updatedKeyStore should not be originalKeyStore
    }
  }

  it should "fail if invalid arguments are provided" in {
    val directory = createApplicationDirectory(init = _ => ())

    val service = new Service with TestServiceArguments {
      override protected def applicationDirectory: ApplicationDirectory = directory
      override protected def console: Option[Console] = None
      override protected def applicationTray: ApplicationTray = ApplicationTray.NoOp()

      override def raw: Array[String] = Array("invalid")
    }

    eventually[Assertion] {
      service.state match {
        case Service.State.StartupFailed(e: ServiceStartupFailure) =>
          e.cause should be("unknown")
          e.message should include("Invalid arguments provided")

        case other =>
          fail(s"Unexpected result received: [$other]")
      }
    }
  }

  it should "support generating UI start commands" in {
    Service.startUiCommand(osName = "Linux", userHome = "/a/b/c") should be("stasis-ui")

    Service.startUiCommand(osName = "Mac OS X", userHome = "/a/b/c") should be("open /a/b/c/Applications/stasis.app")

    an[IllegalArgumentException] should be thrownBy Service.startUiCommand(osName = "Windows", userHome = "/a/b/c")
  }

  it should "support creating service callbacks" in {
    val service = mock[Service]
    val runtime = mock[Runtime]

    val callbacks = Service.createCallbacks(forService = service, withRuntime = runtime)

    callbacks.terminateService()
    verify(service).stop()

    val expectedUiCommand = Service.startUiCommand()

    callbacks.startUiService()
    verify(runtime).exec(expectedUiCommand)

    succeed
  }

  "Service Arguments" should "retry retrieving raw arguments until they are available" in {
    val expectedMode = ApplicationArguments.Mode.Service

    val minDelay = Service.Arguments.RetryDelay / 2
    val maxDelay = Service.Arguments.RetryDelay * (Service.Arguments.RetryAttempts - 1L)

    (0 until 25).foreach { i =>
      val rnd: Random = ThreadLocalRandom.current()
      val delay = rnd.between(minDelay.toMillis, maxDelay.toMillis).millis

      var rawArgs: Array[String] = null

      after(delay = delay, using = typedSystem) {
        rawArgs = Array("service")
        Future.successful(Done)
      }

      withClue(s"Iteration number [$i] with delay [$delay]") {
        noException should be thrownBy {
          val instance = new Service.Arguments { override def raw: Array[String] = rawArgs }
          val arguments = instance.arguments(applicationName = "test-application").await

          arguments should be(ApplicationArguments(mode = expectedMode))
        }
      }
    }

    succeed
  }

  they should "fail retrieving raw arguments if none are provided" in {
    // none provided (the main method in App would not have been called)
    val instance = new Service.Arguments {}

    instance
      .arguments(applicationName = "test-application")
      .failed
      .map { e =>
        e.getMessage should be("No arguments provided")
      }
  }

  they should "retrieve application arguments" in {
    val expectedMode = ApplicationArguments.Mode.Service

    val instance = new Service.Arguments {
      override def raw: Array[String] = Array.empty
    }

    instance
      .arguments(applicationName = "test-application")
      .map { arguments =>
        arguments should be(ApplicationArguments(mode = expectedMode))
      }
  }

  private def mockConsole(username: String, password: String): Console = {
    val mockConsole = mock[java.io.Console]
    when(mockConsole.readLine("Username: ")).thenReturn(username)
    when(mockConsole.readPassword("Password: ")).thenReturn(password.toCharArray)

    mockConsole
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
    "ServiceSpec"
  )

  private val ports: mutable.Queue[Int] = (31000 to 31100).to(mutable.Queue)

  private val username = "test-user"
  private val password = "test-password"
  private val encryptedDeviceSecret = "08ko0LWDalPvEHna/WWuq3LoEaA3m4dQ1QuP".decodeFromBase64 // decrypted == "test-secret"

  private val tokenEndpointConfigEntry = "stasis.client.server.authentication.token-endpoint"
  private val apiTerminationDelayEntry = "stasis.client.service.termination-delay"
  private val apiEndpointConfigEntry = "stasis.client.api.http.port"
  private val apiInitConfigEntry = "stasis.client.api.init.port"

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 250.milliseconds)

  private trait TestServiceArguments extends Service.Arguments {
    override def raw: Array[String] = Array.empty
  }
}
