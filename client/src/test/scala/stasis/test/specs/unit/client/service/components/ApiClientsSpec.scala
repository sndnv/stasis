package stasis.test.specs.unit.client.service.components

import java.util.UUID

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.model.headers.HttpCredentials
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken
import org.apache.pekko.stream.StreamTcpException
import org.apache.pekko.util.ByteString
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.client.api.clients.Clients
import stasis.client.encryption.Aes
import stasis.client.encryption.secrets.DeviceSecret
import stasis.client.security.CredentialsProvider
import stasis.client.service.ApplicationTray
import stasis.client.service.components.ApiClients
import stasis.client.service.components.Base
import stasis.client.service.components.Files
import stasis.client.service.components.Secrets
import stasis.core.discovery.ServiceApiClient
import stasis.core.discovery.ServiceApiEndpoint
import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.packaging.Crate
import stasis.shared.model.users.User
import stasis.shared.secrets.SecretsConfig
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers
import stasis.test.specs.unit.client.mocks.MockServerApiEndpoint
import stasis.test.specs.unit.client.mocks.MockServerApiEndpointClient
import stasis.test.specs.unit.client.mocks.MockServerCoreEndpointClient
import stasis.test.specs.unit.core.discovery.mocks.MockServiceDiscoveryClient
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class ApiClientsSpec extends AsyncUnitSpec with ResourceHelpers {
  "An ApiClients component" should "create itself from config" in {
    val serverApiCredentials = OAuth2BearerToken(token = "test-token")

    val serverApiEndpointPort = ports.dequeue()
    val serverApiEndpoint = new MockServerApiEndpoint(expectedCredentials = serverApiCredentials)
      .start(port = serverApiEndpointPort)

    val deviceId = UUID.fromString("bc3b2b9a-3d04-4c8c-a6bb-b4ee428d1a99")
    val apiUrl = s"http://localhost:$serverApiEndpointPort"

    val coreNodeId = UUID.fromString("31f7c5b1-3d47-4731-8c2b-19f6416eb2e3")
    val coreAddress = "http://localhost:19091"

    implicit val secretsConfig: SecretsConfig = SecretsConfig(
      config = typedSystem.settings.config.getConfig("stasis.client.secrets"),
      ivSize = Aes.IvSize
    )

    val directory = createApplicationDirectory(
      init = dir => {
        val path = dir.config.get
        java.nio.file.Files.createDirectories(path)

        java.nio.file.Files.writeString(
          path.resolve(Files.ConfigOverride),
          s"""{$serverApiEndpointConfigEntry: "$apiUrl"}"""
        )
      }
    )

    for {
      apiClients <- ApiClients(
        base = Base(applicationDirectory = directory, applicationTray = ApplicationTray.NoOp(), terminate = () => ()).await,
        secrets = new Secrets {
          override def deviceSecret: DeviceSecret =
            DeviceSecret(
              user = User.generateId(),
              device = deviceId,
              secret = ByteString.empty
            )

          override def credentialsProvider: CredentialsProvider =
            new CredentialsProvider {
              override def core: Future[HttpCredentials] = Future.successful(serverApiCredentials)
              override def api: Future[HttpCredentials] = Future.successful(serverApiCredentials)
            }

          override def config: SecretsConfig = secretsConfig

          override def verifyUserPassword: Array[Char] => Boolean = _ => false

          override def updateUserCredentials: (Clients, Array[Char], String) => Future[Done] =
            (_, _, _) => Future.successful(Done)

          override def reEncryptDeviceSecret: (Clients, Array[Char]) => Future[Done] =
            (_, _) => Future.successful(Done)
        }
      )
      _ <- apiClients.clients.api.ping() // expected to succeed
      pullFailure <- apiClients.clients.core.pull(Crate.generateId()).failed
      _ <- serverApiEndpoint.map(_.terminate(100.millis))
    } yield {
      apiClients.clients.api.self should be(deviceId)
      apiClients.clients.api.server should be(apiUrl)

      apiClients.clients.core.self should be(coreNodeId)
      apiClients.clients.core.server should be(coreAddress)

      pullFailure shouldBe a[StreamTcpException]
      pullFailure.getMessage should include("Connection refused")
    }
  }

  "An ApiClients ServiceApiClientFactory" should "support creating API clients" in {
    val expectedClient = MockServerApiEndpointClient()

    val factory = new ApiClients.ServiceApiClientFactory(
      createServerCoreEndpointClient = _ => throw new UnsupportedOperationException(),
      createServerApiEndpointClient = (_, _) => expectedClient,
      createServiceDiscoveryClient = _ => throw new UnsupportedOperationException()
    )

    val actualClient = factory.create(
      endpoint = ServiceApiEndpoint.Api(uri = "test-uri"),
      coreClient = MockServerCoreEndpointClient()
    )

    actualClient should be(expectedClient)
  }

  it should "fail to create API clients if an invalid core client is provided" in {
    val factory = new ApiClients.ServiceApiClientFactory(
      createServerCoreEndpointClient = _ => throw new UnsupportedOperationException(),
      createServerApiEndpointClient = (_, _) => MockServerApiEndpointClient(),
      createServiceDiscoveryClient = _ => throw new UnsupportedOperationException()
    )

    val e = intercept[IllegalArgumentException](
      factory.create(
        endpoint = ServiceApiEndpoint.Api(uri = "test-uri"),
        coreClient = new ServiceApiClient {}
      )
    )

    e.getMessage should be("Cannot create API endpoint client with core client of type []") // anonymous class; name is empty
  }

  it should "support creating core clients" in {
    val expectedClient = MockServerCoreEndpointClient()

    val factory = new ApiClients.ServiceApiClientFactory(
      createServerCoreEndpointClient = _ => expectedClient,
      createServerApiEndpointClient = (_, _) => throw new UnsupportedOperationException(),
      createServiceDiscoveryClient = _ => throw new UnsupportedOperationException()
    )

    val actualClient = factory.create(
      endpoint = ServiceApiEndpoint.Core(address = HttpEndpointAddress(uri = "test-uri"))
    )

    actualClient should be(expectedClient)
  }

  it should "fail to create core clients if an unsupported core address is provided" in {
    val expectedClient = MockServerCoreEndpointClient()

    val factory = new ApiClients.ServiceApiClientFactory(
      createServerCoreEndpointClient = _ => expectedClient,
      createServerApiEndpointClient = (_, _) => throw new UnsupportedOperationException(),
      createServiceDiscoveryClient = _ => throw new UnsupportedOperationException()
    )

    val e = intercept[IllegalArgumentException](
      factory.create(
        endpoint = ServiceApiEndpoint.Core(address = GrpcEndpointAddress(host = "localhost", port = 1234, tlsEnabled = false))
      )
    )

    e.getMessage should be("Cannot create core endpoint client for address of type [GrpcEndpointAddress]")
  }

  it should "support creating discovery clients" in {
    val expectedClient = MockServiceDiscoveryClient()

    val factory = new ApiClients.ServiceApiClientFactory(
      createServerCoreEndpointClient = _ => throw new UnsupportedOperationException(),
      createServerApiEndpointClient = (_, _) => throw new UnsupportedOperationException(),
      createServiceDiscoveryClient = _ => expectedClient
    )

    val actualClient = factory.create(
      endpoint = ServiceApiEndpoint.Discovery(uri = "test-uri")
    )

    actualClient should be(expectedClient)
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "ApiClientsSpec"
  )

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

  private val ports: mutable.Queue[Int] = (44000 to 44100).to(mutable.Queue)

  private val serverApiEndpointConfigEntry = "stasis.client.server.api.url"
}
