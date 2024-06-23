package stasis.test.specs.unit.client.api.clients

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials
import org.apache.pekko.http.scaladsl.model.{HttpResponse, StatusCodes}
import org.apache.pekko.stream.scaladsl.{Flow, Source}
import org.apache.pekko.util.ByteString
import com.typesafe.config.Config
import org.scalatest.concurrent.Eventually
import stasis.client.api.clients.DefaultServerApiEndpointClient
import stasis.client.api.clients.exceptions.ServerApiFailure
import stasis.client.encryption.secrets.DeviceMetadataSecret
import stasis.client.model.DatasetMetadata
import stasis.core.networking.exceptions.ClientFailure
import stasis.core.packaging.Crate
import stasis.core.routing.Node
import stasis.core.security.tls.EndpointContext
import stasis.shared.api.requests.{CreateDatasetDefinition, CreateDatasetEntry}
import stasis.shared.api.responses.CreatedDatasetDefinition
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.shared.model.devices.Device
import stasis.shared.model.schedules.Schedule
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.Fixtures
import stasis.test.specs.unit.client.mocks.{MockEncryption, MockServerApiEndpoint, MockServerCoreEndpointClient}
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext
import stasis.test.specs.unit.shared.model.Generators
import java.time.Instant

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

import stasis.shared.api.requests.ResetUserPassword

class DefaultServerApiEndpointClientSpec extends AsyncUnitSpec with Eventually {
  "A DefaultServerApiEndpointClient" should "create dataset definitions" in {
    val apiPort = ports.dequeue()
    val api = new MockServerApiEndpoint(expectedCredentials = apiCredentials)
    api.start(port = apiPort)

    val apiClient = createClient(apiPort)

    val expectedRequest = CreateDatasetDefinition(
      info = "test-definition",
      device = apiClient.self,
      redundantCopies = 1,
      existingVersions = DatasetDefinition.Retention(
        policy = DatasetDefinition.Retention.Policy.All,
        duration = 3.seconds
      ),
      removedVersions = DatasetDefinition.Retention(
        policy = DatasetDefinition.Retention.Policy.All,
        duration = 3.seconds
      )
    )

    for {
      createdDefinition <- apiClient.createDatasetDefinition(request = expectedRequest)
      definitionExists <- api.definitionExists(createdDefinition.definition)
    } yield {
      definitionExists should be(true)
    }
  }

  it should "fail to create dataset definitions if the provided device is not the current device" in {
    val apiPort = ports.dequeue()
    val api = new MockServerApiEndpoint(expectedCredentials = apiCredentials)
    api.start(port = apiPort)

    val apiClient = createClient(apiPort)

    val expectedRequest = CreateDatasetDefinition(
      info = "test-definition",
      device = Device.generateId(),
      redundantCopies = 1,
      existingVersions = DatasetDefinition.Retention(
        policy = DatasetDefinition.Retention.Policy.All,
        duration = 3.seconds
      ),
      removedVersions = DatasetDefinition.Retention(
        policy = DatasetDefinition.Retention.Policy.All,
        duration = 3.seconds
      )
    )

    apiClient
      .createDatasetDefinition(request = expectedRequest)
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e: IllegalArgumentException) =>
        e.getMessage should startWith("Cannot create dataset definition for a different device")
      }
  }

  it should "create dataset entries" in {
    val apiPort = ports.dequeue()
    val api = new MockServerApiEndpoint(expectedCredentials = apiCredentials)
    api.start(port = apiPort)

    val apiClient = createClient(apiPort)

    val expectedRequest = CreateDatasetEntry(
      definition = DatasetDefinition.generateId(),
      device = Device.generateId(),
      data = Set(Crate.generateId(), Crate.generateId()),
      metadata = Crate.generateId()
    )

    for {
      createdEntry <- apiClient.createDatasetEntry(request = expectedRequest)
      entryExists <- api.entryExists(createdEntry.entry)
    } yield {
      entryExists should be(true)
    }
  }

  it should "retrieve dataset definitions" in {
    val device = Device.generateId()

    val apiPort = ports.dequeue()
    val api = new MockServerApiEndpoint(
      expectedCredentials = apiCredentials,
      withDefinitions = Some(
        Seq(
          Generators.generateDefinition.copy(device = device),
          Generators.generateDefinition.copy(device = device)
        )
      )
    )
    api.start(port = apiPort)

    val apiClient = createClient(apiPort, self = device)

    apiClient
      .datasetDefinitions()
      .map { definitions =>
        definitions should not be empty
      }
  }

  it should "not retrieve dataset definitions not for the current device" in {
    val device = Device.generateId()

    val apiPort = ports.dequeue()
    val api = new MockServerApiEndpoint(
      expectedCredentials = apiCredentials,
      withDefinitions = Some(
        Seq(
          Generators.generateDefinition.copy(),
          Generators.generateDefinition.copy(device = device)
        )
      )
    )
    api.start(port = apiPort)

    val apiClient = createClient(apiPort, self = device)

    apiClient
      .datasetDefinitions()
      .map { definitions =>
        definitions.length should be(1)
      }
  }

  it should "retrieve dataset entries for a dataset definition" in {
    val apiPort = ports.dequeue()
    val api = new MockServerApiEndpoint(expectedCredentials = apiCredentials)
    api.start(port = apiPort)

    val apiClient = createClient(apiPort)

    apiClient
      .datasetEntries(definition = DatasetDefinition.generateId())
      .map { entries =>
        entries should not be empty
      }
  }

  it should "retrieve individual dataset definitions" in {
    val device = Device.generateId()
    val expectedDefinition = DatasetEntry.generateId()

    val apiPort = ports.dequeue()
    val api = new MockServerApiEndpoint(
      expectedCredentials = apiCredentials,
      withDefinitions = Some(
        Seq(
          Generators.generateDefinition.copy(id = expectedDefinition, device = device)
        )
      )
    )
    api.start(port = apiPort)

    val apiClient = createClient(apiPort, self = device)

    apiClient
      .datasetDefinition(definition = expectedDefinition)
      .map { definition =>
        definition.id should be(expectedDefinition)
      }
  }

  it should "not retrieve individual dataset definitions not for the current device" in {
    val device = Device.generateId()
    val definition = Generators.generateDefinition

    val apiPort = ports.dequeue()
    val api = new MockServerApiEndpoint(
      expectedCredentials = apiCredentials,
      withDefinitions = Some(Seq(definition))
    )
    api.start(port = apiPort)

    val apiClient = createClient(apiPort, self = device)

    apiClient
      .datasetDefinition(definition = definition.id)
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e: IllegalArgumentException) =>
        e.getMessage should be(
          "Cannot retrieve dataset definition for a different device"
        )
      }
  }

  it should "retrieve individual dataset entries" in {
    val apiPort = ports.dequeue()
    val api = new MockServerApiEndpoint(expectedCredentials = apiCredentials)
    api.start(port = apiPort)

    val apiClient = createClient(apiPort)

    val expectedEntry = DatasetEntry.generateId()

    apiClient
      .datasetEntry(entry = expectedEntry)
      .map { entry =>
        entry.id should be(expectedEntry)
      }
  }

  it should "retrieve the latest dataset entry for a definition" in {
    val apiPort = ports.dequeue()
    val api = new MockServerApiEndpoint(expectedCredentials = apiCredentials)
    api.start(port = apiPort)

    val apiClient = createClient(apiPort)

    apiClient
      .latestEntry(definition = DatasetDefinition.generateId(), until = None)
      .map { entry =>
        entry should not be empty
      }
  }

  it should "handle missing latest dataset entry for a definition" in {
    val definition = DatasetDefinition.generateId()

    val apiPort = ports.dequeue()
    val api = new MockServerApiEndpoint(
      expectedCredentials = apiCredentials,
      definitionsWithoutEntries = Seq(definition)
    )
    api.start(port = apiPort)

    val apiClient = createClient(apiPort)

    apiClient
      .latestEntry(definition = definition, until = None)
      .map { entry =>
        entry should be(None)
      }
  }

  it should "retrieve the latest dataset entry for a definition until a timestamp" in {
    val apiPort = ports.dequeue()
    val api = new MockServerApiEndpoint(expectedCredentials = apiCredentials)
    api.start(port = apiPort)

    val apiClient = createClient(apiPort)

    apiClient
      .latestEntry(definition = DatasetDefinition.generateId(), until = Some(Instant.now()))
      .map { entry =>
        entry should not be empty
      }
  }

  it should "retrieve public schedules" in {
    val apiPort = ports.dequeue()
    val api = new MockServerApiEndpoint(expectedCredentials = apiCredentials)
    api.start(port = apiPort)

    val apiClient = createClient(apiPort)

    apiClient
      .publicSchedules()
      .map { schedules =>
        schedules.forall(_.isPublic) should be(true)
        schedules should not be empty
      }
  }

  it should "retrieve individual public schedules" in {
    val apiPort = ports.dequeue()
    val api = new MockServerApiEndpoint(expectedCredentials = apiCredentials)
    api.start(port = apiPort)

    val apiClient = createClient(apiPort)

    val expectedSchedule = Schedule.generateId()

    apiClient
      .publicSchedule(schedule = expectedSchedule)
      .map { schedule =>
        schedule.isPublic should be(true)
        schedule.id should be(expectedSchedule)
      }
  }

  it should "retrieve dataset metadata (with entry ID)" in {
    val apiPort = ports.dequeue()
    val api = new MockServerApiEndpoint(expectedCredentials = apiCredentials)
    api.start(port = apiPort)

    val apiClient = createClient(apiPort)

    apiClient
      .datasetMetadata(entry = DatasetEntry.generateId())
      .map { metadata =>
        metadata should be(DatasetMetadata.empty)
      }
  }

  it should "retrieve dataset metadata (with entry)" in {
    val apiPort = ports.dequeue()
    val api = new MockServerApiEndpoint(expectedCredentials = apiCredentials)
    api.start(port = apiPort)

    val apiClient = createClient(apiPort)

    apiClient
      .datasetMetadata(entry = Generators.generateEntry)
      .map { metadata =>
        metadata should be(DatasetMetadata.empty)
      }
  }

  it should "fail to retrieve dataset metadata without a decryption context" in {
    val apiPort = ports.dequeue()
    val api = new MockServerApiEndpoint(expectedCredentials = apiCredentials)
    api.start(port = apiPort)

    val apiClient = createClient(apiPort, decryption = DefaultServerApiEndpointClient.DecryptionContext.Disabled)

    apiClient
      .datasetMetadata(entry = Generators.generateEntry)
      .failed
      .map { e =>
        e should be(an[IllegalStateException])
        e.getMessage should be("Cannot retrieve dataset metadata; decryption context is disabled")
      }
  }

  it should "retrieve current user" in {
    val apiPort = ports.dequeue()
    val api = new MockServerApiEndpoint(expectedCredentials = apiCredentials)
    api.start(port = apiPort)

    val apiClient = createClient(apiPort)

    apiClient
      .user()
      .map { user =>
        user.active should be(true)
      }
  }

  it should "reset the current user's salt" in {
    val apiPort = ports.dequeue()
    val api = new MockServerApiEndpoint(expectedCredentials = apiCredentials)
    api.start(port = apiPort)

    val apiClient = createClient(apiPort)

    apiClient
      .resetUserSalt()
      .map { salt =>
        salt.salt should be("updated-salt")
      }
  }

  it should "update the current user's password" in {
    val apiPort = ports.dequeue()
    val api = new MockServerApiEndpoint(expectedCredentials = apiCredentials)
    api.start(port = apiPort)

    val apiClient = createClient(apiPort)

    apiClient
      .resetUserPassword(request = ResetUserPassword(rawPassword = "updated-password"))
      .map { _ =>
        succeed
      }
  }

  it should "retrieve current device" in {
    val apiPort = ports.dequeue()
    val api = new MockServerApiEndpoint(expectedCredentials = apiCredentials)
    api.start(port = apiPort)

    val apiClient = createClient(apiPort)

    apiClient
      .device()
      .map { device =>
        device.active should be(true)
        device.id should be(apiClient.self)
      }
  }

  it should "push current device key" in {
    val apiPort = ports.dequeue()
    val api = new MockServerApiEndpoint(expectedCredentials = apiCredentials)
    api.start(port = apiPort)

    val apiClient = createClient(apiPort)

    for {
      _ <- apiClient.pushDeviceKey(ByteString("test-key"))
      deviceKeyExists <- api.deviceKeyExists(apiClient.self)
    } yield {
      deviceKeyExists should be(true)
    }
  }

  it should "pull current device key" in {
    val expectedKey = ByteString("test-key")

    val apiPort = ports.dequeue()
    val api = new MockServerApiEndpoint(expectedCredentials = apiCredentials, expectedDeviceKey = Some(expectedKey))
    api.start(port = apiPort)

    val apiClient = createClient(apiPort)

    apiClient
      .pullDeviceKey()
      .map { key =>
        key should be(Some(expectedKey))
      }
  }

  it should "fail to pull missing device key" in {
    val apiPort = ports.dequeue()
    val api = new MockServerApiEndpoint(expectedCredentials = apiCredentials)
    api.start(port = apiPort)

    val apiClient = createClient(apiPort)

    apiClient
      .pullDeviceKey()
      .map { key =>
        key should be(None)
      }
  }

  it should "make ping requests" in {
    val apiPort = ports.dequeue()
    val api = new MockServerApiEndpoint(expectedCredentials = apiCredentials)
    api.start(port = apiPort)

    val apiClient = createClient(apiPort)

    noException should be thrownBy apiClient.ping().await
  }

  it should "handle unexpected responses" in {
    import DefaultServerApiEndpointClient._
    import stasis.shared.api.Formats._

    val response = HttpResponse(status = StatusCodes.OK, entity = "unexpected-response-entity")

    response
      .to[CreatedDatasetDefinition]
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e: ServerApiFailure) =>
        e.status should be(StatusCodes.InternalServerError)

        e.getMessage should be(
          "Server API request unmarshalling failed with: [Unsupported Content-Type [Some(text/plain; charset=UTF-8)], supported: application/json]"
        )
      }
  }

  it should "handle endpoint failures" in {
    import DefaultServerApiEndpointClient._
    import stasis.shared.api.Formats._

    val status = StatusCodes.NotFound
    val message = "Test Failure"
    val response = HttpResponse(status = status, entity = message)

    response
      .to[CreatedDatasetDefinition]
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e: ServerApiFailure) =>
        e.status should be(status)

        e.getMessage should be(
          s"Server API request failed with [$status]: [$message]"
        )
      }
  }

  it should "handle pool client failures" in {
    import DefaultServerApiEndpointClient._

    val future: Future[HttpResponse] = Future.failed(ClientFailure("test failure"))

    future.transformClientFailures().failed.map {
      case e: ServerApiFailure =>
        e.status should be(StatusCodes.InternalServerError)
        e.getMessage should be("test failure")

      case other =>
        fail(s"Unexpected result received: [$other]")
    }
  }

  it should "support custom connection contexts" in {
    val apiPort = ports.dequeue()
    val api = new MockServerApiEndpoint(expectedCredentials = apiCredentials)

    val config: Config = typedSystem.settings.config.getConfig("stasis.test.client.security.tls")

    val endpointContext = EndpointContext(
      config = EndpointContext.Config(config.getConfig("context-server"))
    )

    val clientContext = EndpointContext(
      config = EndpointContext.Config(config.getConfig("context-client"))
    )

    api.start(port = apiPort, context = Some(endpointContext))

    val apiClient = createClient(apiPort, context = Some(clientContext))

    noException should be thrownBy apiClient.ping().await
  }

  private def createClient(
    apiPort: Int,
    self: Device.Id = Device.generateId(),
    decryption: DefaultServerApiEndpointClient.DecryptionContext = defaultContext(),
    context: Option[EndpointContext] = None
  ): DefaultServerApiEndpointClient = {
    val client = new DefaultServerApiEndpointClient(
      apiUrl = context match {
        case Some(_) => s"https://localhost:$apiPort"
        case None    => s"http://localhost:$apiPort"
      },
      credentials = Future.successful(apiCredentials),
      self = self,
      decryption = decryption,
      context = context,
      requestBufferSize = 100
    )

    eventually[Unit] {
      // ensures the endpoint has started; pekko is expected to retry GET requests
      val _ = client.ping().await
    }

    client
  }

  private def defaultContext(): DefaultServerApiEndpointClient.DecryptionContext =
    DefaultServerApiEndpointClient.DecryptionContext(
      core = new MockServerCoreEndpointClient(self = Node.generateId(), crates = Map.empty) {
        override def pull(crate: Crate.Id): Future[Option[Source[ByteString, NotUsed]]] =
          Future.successful(Some(Source.single(ByteString("test-crate"))))
      },
      deviceSecret = Fixtures.Secrets.Default,
      decoder = new MockEncryption() {
        override def decrypt(metadataSecret: DeviceMetadataSecret): Flow[ByteString, ByteString, NotUsed] =
          Flow[ByteString].mapAsync(parallelism = 1)(_ => DatasetMetadata.toByteString(DatasetMetadata.empty))
      }
    )

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "DefaultServerApiEndpointClientSpec"
  )

  private implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

  private val ports: mutable.Queue[Int] = (22000 to 22100).to(mutable.Queue)

  private val apiCredentials = BasicHttpCredentials(username = "some-user", password = "some-password")

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 250.milliseconds)
}
