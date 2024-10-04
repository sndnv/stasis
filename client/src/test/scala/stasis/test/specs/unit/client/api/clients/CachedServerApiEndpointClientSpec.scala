package stasis.test.specs.unit.client.api.clients

import java.time.Instant

import scala.concurrent.duration._

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.util.ByteString

import stasis.client.api.clients.CachedServerApiEndpointClient
import stasis.core.packaging.Crate
import stasis.shared.api.requests.CreateDatasetDefinition
import stasis.shared.api.requests.CreateDatasetEntry
import stasis.shared.api.requests.ResetUserPassword
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.datasets.DatasetEntry
import stasis.shared.model.devices.Device
import stasis.shared.model.schedules.Schedule
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.mocks.MockServerApiEndpointClient
import stasis.test.specs.unit.shared.model.Generators

class CachedServerApiEndpointClientSpec extends AsyncUnitSpec {
  "A CachedServerApiEndpointClient" should "create dataset definitions, without caching" in {
    val mockApiClient = MockServerApiEndpointClient()
    val client = createClient(underlying = mockApiClient)

    val request = CreateDatasetDefinition(
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

    for {
      _ <- client.createDatasetDefinition(request = request)
      _ <- client.createDatasetDefinition(request = request)
      _ <- client.createDatasetDefinition(request = request)
    } yield {
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated) should be(3)
    }
  }

  it should "create dataset entries, without caching" in {
    val mockApiClient = MockServerApiEndpointClient()
    val client = createClient(underlying = mockApiClient)

    val request = CreateDatasetEntry(
      definition = DatasetDefinition.generateId(),
      device = Device.generateId(),
      data = Set(Crate.generateId(), Crate.generateId()),
      metadata = Crate.generateId()
    )

    for {
      _ <- client.createDatasetEntry(request = request)
      _ <- client.createDatasetEntry(request = request)
      _ <- client.createDatasetEntry(request = request)
    } yield {
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryCreated) should be(3)
    }
  }

  it should "retrieve dataset definitions, without caching" in {
    val mockApiClient = MockServerApiEndpointClient()
    val client = createClient(underlying = mockApiClient)

    for {
      _ <- client.datasetDefinitions()
      _ <- client.datasetDefinitions()
      _ <- client.datasetDefinitions()
    } yield {
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved) should be(3)
    }
  }

  it should "retrieve dataset entries for a dataset definition, without caching" in {
    val mockApiClient = MockServerApiEndpointClient()
    val client = createClient(underlying = mockApiClient)

    for {
      _ <- client.datasetEntries(definition = DatasetDefinition.generateId())
      _ <- client.datasetEntries(definition = DatasetDefinition.generateId())
      _ <- client.datasetEntries(definition = DatasetDefinition.generateId())
    } yield {
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved) should be(3)
    }
  }

  it should "retrieve individual dataset definitions, with caching" in {
    val mockApiClient = MockServerApiEndpointClient()
    val client = createClient(underlying = mockApiClient)

    val definition = DatasetDefinition.generateId()

    for {
      _ <- client.datasetDefinition(definition = definition)
      _ <- client.datasetDefinition(definition = definition)
      _ <- client.datasetDefinition(definition = definition)
    } yield {
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved) should be(1)
    }
  }

  it should "retrieve individual dataset entries, with caching" in {
    val mockApiClient = MockServerApiEndpointClient()
    val client = createClient(underlying = mockApiClient)

    val entry = DatasetEntry.generateId()

    for {
      _ <- client.datasetEntry(entry = entry)
      _ <- client.datasetEntry(entry = entry)
      _ <- client.datasetEntry(entry = entry)
    } yield {
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved) should be(1)
    }
  }

  it should "retrieve the latest dataset entry for a definition, without caching" in {
    val mockApiClient = MockServerApiEndpointClient()
    val client = createClient(underlying = mockApiClient)

    val definition = DatasetDefinition.generateId()

    for {
      _ <- client.latestEntry(definition = definition, until = None)
      _ <- client.latestEntry(definition = definition, until = None)
      _ <- client.latestEntry(definition = definition, until = None)
    } yield {
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest) should be(3)
    }
  }

  it should "retrieve the latest dataset entry for a definition until a timestamp, without caching" in {
    val mockApiClient = MockServerApiEndpointClient()
    val client = createClient(underlying = mockApiClient)

    val definition = DatasetDefinition.generateId()
    val until = Some(Instant.now())

    for {
      _ <- client.latestEntry(definition = definition, until = until)
      _ <- client.latestEntry(definition = definition, until = until)
      _ <- client.latestEntry(definition = definition, until = until)
    } yield {
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest) should be(3)
    }
  }

  it should "retrieve public schedules, without caching" in {
    val mockApiClient = MockServerApiEndpointClient()
    val client = createClient(underlying = mockApiClient)

    for {
      _ <- client.publicSchedules()
      _ <- client.publicSchedules()
      _ <- client.publicSchedules()
    } yield {
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved) should be(3)
    }
  }

  it should "retrieve individual public schedules, without caching" in {
    val mockApiClient = MockServerApiEndpointClient()
    val client = createClient(underlying = mockApiClient)

    val schedule = Schedule.generateId()

    for {
      _ <- client.publicSchedule(schedule = schedule)
      _ <- client.publicSchedule(schedule = schedule)
      _ <- client.publicSchedule(schedule = schedule)
    } yield {
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved) should be(3)
    }
  }

  it should "retrieve dataset metadata (with entry ID), with caching" in {
    val mockApiClient = MockServerApiEndpointClient()
    val client = createClient(underlying = mockApiClient)

    val entry = DatasetEntry.generateId()

    for {
      _ <- client.datasetMetadata(entry = entry)
      _ <- client.datasetMetadata(entry = entry)
      _ <- client.datasetMetadata(entry = entry)
    } yield {
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved) should be(1)
    }
  }

  it should "retrieve dataset metadata (with entry), with caching" in {
    val mockApiClient = MockServerApiEndpointClient()
    val client = createClient(underlying = mockApiClient)

    val entry = Generators.generateEntry

    for {
      _ <- client.datasetMetadata(entry = entry)
      _ <- client.datasetMetadata(entry = entry)
      _ <- client.datasetMetadata(entry = entry)
    } yield {
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved) should be(1)
    }
  }

  it should "retrieve current user, without caching" in {
    val mockApiClient = MockServerApiEndpointClient()
    val client = createClient(underlying = mockApiClient)

    for {
      _ <- client.user()
      _ <- client.user()
      _ <- client.user()
    } yield {
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserRetrieved) should be(3)
    }
  }

  it should "reset the current user's salt, without caching" in {
    val mockApiClient = MockServerApiEndpointClient()
    val client = createClient(underlying = mockApiClient)

    for {
      _ <- client.resetUserSalt()
      _ <- client.resetUserSalt()
      _ <- client.resetUserSalt()
    } yield {
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserSaltReset) should be(3)
    }
  }

  it should "update the current user's password, without caching" in {
    val mockApiClient = MockServerApiEndpointClient()
    val client = createClient(underlying = mockApiClient)

    val request = ResetUserPassword(rawPassword = "test")

    for {
      _ <- client.resetUserPassword(request = request)
      _ <- client.resetUserPassword(request = request)
      _ <- client.resetUserPassword(request = request)
    } yield {
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserPasswordUpdated) should be(3)
    }
  }

  it should "retrieve current device, without caching" in {
    val mockApiClient = MockServerApiEndpointClient()
    val client = createClient(underlying = mockApiClient)

    for {
      _ <- client.device()
      _ <- client.device()
      _ <- client.device()
    } yield {
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceRetrieved) should be(3)
    }
  }

  it should "push current device key, without caching" in {
    val mockApiClient = MockServerApiEndpointClient()
    val client = createClient(underlying = mockApiClient)

    val key = ByteString("test-key")

    for {
      _ <- client.pushDeviceKey(key)
      _ <- client.pushDeviceKey(key)
      _ <- client.pushDeviceKey(key)
    } yield {
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPushed) should be(3)
    }
  }

  it should "pull current device key, without caching" in {
    val mockApiClient = MockServerApiEndpointClient()
    val client = createClient(underlying = mockApiClient)

    for {
      _ <- client.pullDeviceKey()
      _ <- client.pullDeviceKey()
      _ <- client.pullDeviceKey()
    } yield {
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPulled) should be(3)
    }
  }

  it should "check current device key, without caching" in {
    val mockApiClient = MockServerApiEndpointClient()
    val client = createClient(underlying = mockApiClient)

    for {
      _ <- client.deviceKeyExists()
      _ <- client.deviceKeyExists()
      _ <- client.deviceKeyExists()
    } yield {
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyExists) should be(3)
    }
  }

  it should "make ping requests, without caching" in {
    val mockApiClient = MockServerApiEndpointClient()
    val client = createClient(underlying = mockApiClient)

    for {
      _ <- client.ping()
      _ <- client.ping()
      _ <- client.ping()
    } yield {
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Ping) should be(3)
    }
  }

  private def createClient(underlying: MockServerApiEndpointClient): CachedServerApiEndpointClient =
    new CachedServerApiEndpointClient(
      config = CachedServerApiEndpointClient.Config(
        initialCapacity = 10,
        maximumCapacity = 100,
        timeToLive = 1.second,
        timeToIdle = 1.second
      ),
      underlying = underlying
    )

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "CachedServerApiEndpointClientSpec"
  )
}
