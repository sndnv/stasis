package stasis.test.specs.unit.client.api.clients

import java.time.Instant

import akka.NotUsed
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.http.scaladsl.HttpsConnectionContext
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.stream.scaladsl.{Flow, Source}
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.ByteString
import com.typesafe.config.{Config, ConfigFactory}
import stasis.client.api.clients.DefaultServerApiEndpointClient
import stasis.client.api.clients.exceptions.ServerApiFailure
import stasis.client.encryption.secrets.DeviceMetadataSecret
import stasis.client.model.DatasetMetadata
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
import stasis.test.specs.unit.shared.model.Generators

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

class DefaultServerApiEndpointClientSpec extends AsyncUnitSpec {
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
    val apiPort = ports.dequeue()
    val api = new MockServerApiEndpoint(expectedCredentials = apiCredentials)
    api.start(port = apiPort)

    val apiClient = createClient(apiPort)

    apiClient
      .datasetDefinitions()
      .map { definitions =>
        definitions should not be empty
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
    val apiPort = ports.dequeue()
    val api = new MockServerApiEndpoint(expectedCredentials = apiCredentials)
    api.start(port = apiPort)

    val apiClient = createClient(apiPort)

    val expectedDefinition = DatasetEntry.generateId()

    apiClient
      .datasetDefinition(definition = expectedDefinition)
      .map { definition =>
        definition.id should be(expectedDefinition)
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

  it should "retrieve  individual public schedules" in {
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
      .recover {
        case NonFatal(e: ServerApiFailure) =>
          e.getMessage should be(
            "Server API request unmarshalling failed with: [Unsupported Content-Type, supported: application/json]"
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
      .recover {
        case NonFatal(e: ServerApiFailure) =>
          e.getMessage should be(
            s"Server API request failed with [$status]: [$message]"
          )
      }
  }

  it should "support custom connection contexts" in {
    val apiPort = ports.dequeue()
    val api = new MockServerApiEndpoint(expectedCredentials = apiCredentials)

    val config: Config = ConfigFactory.load().getConfig("stasis.test.client.security.tls")

    val endpointContext = EndpointContext.create(
      contextConfig = EndpointContext.ContextConfig(config.getConfig("context-server"))
    )

    val clientContext = EndpointContext.create(
      contextConfig = EndpointContext.ContextConfig(config.getConfig("context-client"))
    )

    api.start(port = apiPort, context = Some(endpointContext))

    val apiClient = createClient(apiPort, context = Some(clientContext))

    noException should be thrownBy apiClient.ping().await
  }

  private def createClient(apiPort: Int, context: Option[HttpsConnectionContext] = None) =
    new DefaultServerApiEndpointClient(
      apiUrl = context match {
        case Some(_) => s"https://localhost:$apiPort"
        case None    => s"http://localhost:$apiPort"
      },
      credentials = Future.successful(apiCredentials),
      self = Device.generateId(),
      decryption = DefaultServerApiEndpointClient.DecryptionContext(
        core = new MockServerCoreEndpointClient(self = Node.generateId(), crates = Map.empty) {
          override def pull(crate: Crate.Id): Future[Option[Source[ByteString, NotUsed]]] =
            Future.successful(Some(Source.single(ByteString("test-crate"))))
        },
        deviceSecret = Fixtures.Secrets.Default,
        decoder = new MockEncryption() {
          override def decrypt(metadataSecret: DeviceMetadataSecret): Flow[ByteString, ByteString, NotUsed] =
            Flow[ByteString].map(_ => DatasetMetadata.toByteString(DatasetMetadata.empty))
        }
      ),
      context = context
    )

  private implicit val typedSystem: ActorSystem[SpawnProtocol] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol.behavior): Behavior[SpawnProtocol],
    "DefaultServerApiEndpointClientSpec"
  )

  private implicit val untypedSystem: akka.actor.ActorSystem = typedSystem.toUntyped

  private implicit val mat: Materializer = ActorMaterializer()

  private val ports: mutable.Queue[Int] = (22000 to 22100).to[mutable.Queue]

  private val apiCredentials = BasicHttpCredentials(username = "some-user", password = "some-password")
}
