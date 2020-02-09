package stasis.test.specs.unit.client.service

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, StatusCodes}
import com.typesafe.{config => typesafe}
import org.scalatest.concurrent.Eventually
import stasis.client.service.components.Files
import stasis.client.service.{ApplicationDirectory, CredentialsReader, Service}
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.mocks.MockTokenEndpoint
import stasis.test.specs.unit.client.{EncodingHelpers, ResourceHelpers}

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

class ServiceSpec extends AsyncUnitSpec with ResourceHelpers with EncodingHelpers with Eventually {
  "A Service" should "handle API endpoint requests" in {
    val encryptedDeviceSecret = "08ko0LWDalPvEHna/WWuq3LoEaA3m4dQ1QuP".decodeFromBase64 // decrypted == "test-secret"

    val username = "test-user"
    val password = "test-password".toCharArray

    val tokenEndpointPort = ports.dequeue()
    val tokenEndpointConfigEntry = "stasis.client.server.authentication.token-endpoint"

    val apiTerminationDelay = 100.millis
    val apiTerminationDelayEntry = "stasis.client.api.termination-delay"

    val tokenEndpoint = new MockTokenEndpoint(port = tokenEndpointPort, token = "test-token")
    tokenEndpoint.start()

    val apiEndpointPort = ports.dequeue()
    val apiEndpointConfigEntry = "stasis.client.api.http.port"

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
             |$apiEndpointConfigEntry: $apiEndpointPort
             }""".stripMargin.replaceAll("\n", "")
        )
      }
    )

    val service = new Service {
      override protected def applicationDirectory: ApplicationDirectory =
        directory

      override protected def credentialsReader: CredentialsReader =
        () => Success((username, password))
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
    await(delay = apiTerminationDelay * 2, withSystem = untypedSystem)

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
      override protected def applicationDirectory: ApplicationDirectory =
        directory
    }

    eventually {
      service.state match {
        case Service.State.StartupFailed(e: typesafe.ConfigException.WrongType) =>
          e.getMessage should include("secrets has type STRING")

        case other =>
          fail(s"Unexpected result received: [$other]")
      }
    }
  }

  it should "handle user input failures" in {
    val service = new Service {

      override protected def applicationName: String =
        s"${super.applicationName}-test"

      override protected def credentialsReader: CredentialsReader =
        () => Failure(new RuntimeException("test failure"))
    }

    eventually {
      service.state match {
        case Service.State.StartupFailed(e: RuntimeException) =>
          e.getMessage should be("test failure")

        case other =>
          fail(s"Unexpected result received: [$other]")
      }
    }
  }

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 250.milliseconds)

  private implicit val untypedSystem: ActorSystem = ActorSystem(name = "ServiceSpec")

  private val ports: mutable.Queue[Int] = (31000 to 31100).to[mutable.Queue]
}
