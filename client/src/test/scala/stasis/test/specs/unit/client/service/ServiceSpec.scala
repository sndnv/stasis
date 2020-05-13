package stasis.test.specs.unit.client.service

import java.io.Console

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, StatusCodes}
import org.mockito.scalatest.AsyncMockitoSugar
import org.scalatest.concurrent.Eventually
import stasis.client.service.components.Files
import stasis.client.service.components.exceptions.ServiceStartupFailure
import stasis.client.service.{ApplicationDirectory, Service}
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.mocks.MockTokenEndpoint
import stasis.test.specs.unit.client.{EncodingHelpers, ResourceHelpers}

import scala.collection.mutable
import scala.concurrent.duration._
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

    val service = new Service {
      override protected def applicationDirectory: ApplicationDirectory = directory
      override protected def console: Option[Console] = Some(mockConsole(username, password))
    }

    eventually {
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

    stopResponse.status should be(StatusCodes.Accepted)
    await(delay = apiTerminationDelay * 3, withSystem = untypedSystem)

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
      .recover {
        case NonFatal(e) =>
          e.getMessage should include("Connection refused")
      }
  }

  it should "support API init if a console/TTY is not available" in {
    val initEndpointPort = untypedSystem.settings.config.getInt("stasis.client.api.init.port")

    class ExtendedService extends Service {
      def isConsoleAvailable: Boolean = console.isDefined
    }

    val service = new ExtendedService()
    val request = HttpRequest(method = HttpMethods.GET, uri = s"http://localhost:$initEndpointPort/init")

    if (!service.isConsoleAvailable) {
      eventually {
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
        .recover {
          case NonFatal(e) =>
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

    val service = new Service {
      override protected def applicationDirectory: ApplicationDirectory = directory
      override protected def console: Option[Console] = Some(mockConsole(username, password = "invalid-password"))
    }

    eventually {
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

    val service = new Service {
      override protected def applicationDirectory: ApplicationDirectory = directory
      override protected def console: Option[Console] = Some(mockConsole(username, password))
    }

    eventually {
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

    val service = new Service {
      override protected def applicationDirectory: ApplicationDirectory = directory
      override protected def console: Option[Console] = Some(mockConsole(username, password))
    }

    eventually {
      service.state match {
        case Service.State.StartupFailed(e: ServiceStartupFailure) =>
          e.cause should be("unknown")
          e.message should include("MatchError")

        case other =>
          fail(s"Unexpected result received: [$other]")
      }
    }
  }

  private def mockConsole(username: String, password: String): Console = {
    val mockConsole = mock[java.io.Console]
    when(mockConsole.readLine("Username: ")).thenReturn(username)
    when(mockConsole.readPassword("Password: ")).thenReturn(password.toCharArray)

    mockConsole
  }

  private implicit val untypedSystem: ActorSystem = ActorSystem(name = "ServiceSpec")

  private val ports: mutable.Queue[Int] = (31000 to 31100).to[mutable.Queue]

  private val username = "test-user"
  private val password = "test-password"
  private val encryptedDeviceSecret = "08ko0LWDalPvEHna/WWuq3LoEaA3m4dQ1QuP".decodeFromBase64 // decrypted == "test-secret"

  private val tokenEndpointConfigEntry = "stasis.client.server.authentication.token-endpoint"
  private val apiTerminationDelayEntry = "stasis.client.service.termination-delay"
  private val apiEndpointConfigEntry = "stasis.client.api.http.port"
  private val apiInitConfigEntry = "stasis.client.api.init.port"

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 250.milliseconds)
}
