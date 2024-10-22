package stasis.server.api.routes

import java.time.Instant

import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.marshalling.Marshal
import org.apache.pekko.http.scaladsl.model.RequestEntity
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.core.packaging.Crate
import stasis.core.routing.Node
import stasis.server.persistence.datasets.DatasetEntryStore
import stasis.server.persistence.datasets.MockDatasetEntryStore
import stasis.server.persistence.devices.DeviceStore
import stasis.server.persistence.devices.MockDeviceStore
import stasis.server.security.CurrentUser
import stasis.server.security.ResourceProvider
import stasis.server.security.mocks.MockResourceProvider
import stasis.shared.api.requests.CreateDatasetEntry
import stasis.shared.api.responses.CreatedDatasetEntry
import stasis.shared.api.responses.DeletedDatasetEntry
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.datasets.DatasetEntry
import stasis.shared.model.devices.Device
import stasis.shared.model.users.User
import stasis.test.specs.unit.AsyncUnitSpec

class DatasetEntriesSpec extends AsyncUnitSpec with ScalatestRouteTest {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  import stasis.shared.api.Formats._

  "DatasetEntries routes (full permissions)" should "respond with all entries for a definition" in withRetry {
    val fixtures = new TestFixtures {}
    Future.sequence(entries.map(fixtures.entryStore.manage().create)).await

    Get(s"/for-definition/$definition") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[DatasetEntry]] should contain theSameElementsAs entries
    }
  }

  they should "respond with existing entries" in withRetry {
    val fixtures = new TestFixtures {}

    fixtures.entryStore.manage().create(entries.head).await

    Get(s"/${entries.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DatasetEntry] should be(entries.head)
    }
  }

  they should "fail if an entry is missing" in withRetry {
    val fixtures = new TestFixtures {}
    Get(s"/${DatasetEntry.generateId()}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "delete existing entries" in withRetry {
    val fixtures = new TestFixtures {}
    fixtures.entryStore.manage().create(entries.head).await

    Delete(s"/${entries.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedDatasetEntry] should be(DeletedDatasetEntry(existing = true))

      fixtures.entryStore
        .view()
        .get(entries.head.id)
        .map(_ should be(None))
    }
  }

  they should "not delete missing entries" in withRetry {
    val fixtures = new TestFixtures {}

    Delete(s"/${entries.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedDatasetEntry] should be(DeletedDatasetEntry(existing = false))
    }
  }

  "DatasetEntries routes (self permissions)" should "respond with all entries for a definition" in withRetry {
    val fixtures = new TestFixtures {}
    Future.sequence(entries.map(fixtures.entryStore.manage().create)).await
    fixtures.deviceStore.manage().put(userDevice).await

    Get(s"/own/for-definition/$definition") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[DatasetEntry]] should contain theSameElementsAs entries.take(1)
    }
  }

  they should "respond with the latest entry for a definition" in withRetry {
    val fixtures = new TestFixtures {}

    val ownEntries = entries.map(_.copy(device = userDevice.id))

    Future.sequence(ownEntries.map(fixtures.entryStore.manage().create)).await
    fixtures.deviceStore.manage().put(userDevice).await

    Get(s"/own/for-definition/$definition/latest") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DatasetEntry] should be(latestEntry.copy(device = userDevice.id))
    }
  }

  they should "fail to retrieve latest entry if none could be found" in withRetry {
    val fixtures = new TestFixtures {}
    Get(s"/own/for-definition/$definition/latest") ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "respond with the latest entry for a definition up to a timestamp" in withRetry {
    val fixtures = new TestFixtures {}

    val ownEntries = entries.map(_.copy(device = userDevice.id))

    Future.sequence(ownEntries.map(fixtures.entryStore.manage().create)).await
    fixtures.deviceStore.manage().put(userDevice).await

    val until = latestEntry.created.minusSeconds((entryCreationDifference / 2).toSeconds)

    Get(s"/own/for-definition/$definition/latest?until=$until") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DatasetEntry] should be(earliestEntry.copy(device = userDevice.id))
    }
  }

  they should "create new entries for a definition" in withRetry {
    val fixtures = new TestFixtures {}
    fixtures.deviceStore.manage().put(userDevice).await

    Post(s"/own/for-definition/$definition")
      .withEntity(createRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.entryStore
        .view()
        .get(entityAs[CreatedDatasetEntry].entry)
        .map(_.isDefined should be(true))
    }
  }

  they should "fail to create new entries for an unexpected definition" in withRetry {
    val fixtures = new TestFixtures {}
    fixtures.deviceStore.manage().put(userDevice).await

    Post(s"/own/for-definition/${DatasetDefinition.generateId()}")
      .withEntity(createRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  they should "respond with existing entries" in withRetry {
    val fixtures = new TestFixtures {}
    fixtures.deviceStore.manage().put(userDevice).await

    fixtures.entryStore.manage().create(entries.head).await

    Get(s"/own/${entries.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DatasetEntry] should be(entries.head)
    }
  }

  they should "fail if an entry is missing" in withRetry {
    val fixtures = new TestFixtures {}
    Get(s"/own/${DatasetEntry.generateId()}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "delete existing entries" in withRetry {
    val fixtures = new TestFixtures {}
    fixtures.deviceStore.manage().put(userDevice).await
    fixtures.entryStore.manage().create(entries.head.copy(device = userDevice.id)).await

    Delete(s"/own/${entries.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedDatasetEntry] should be(DeletedDatasetEntry(existing = true))

      fixtures.entryStore
        .view()
        .get(entries.head.id)
        .map(_ should be(None))
    }
  }

  they should "not delete missing entries" in withRetry {
    val fixtures = new TestFixtures {}
    fixtures.deviceStore.manage().put(userDevice).await

    Delete(s"/own/${entries.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedDatasetEntry] should be(DeletedDatasetEntry(existing = false))
    }
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "DatasetEntriesSpec"
  )

  private implicit val untypedSystem: org.apache.pekko.actor.ActorSystem = typedSystem.classicSystem

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private trait TestFixtures {
    lazy val deviceStore: DeviceStore = MockDeviceStore()

    lazy val entryStore: DatasetEntryStore = MockDatasetEntryStore()

    implicit lazy val provider: ResourceProvider = new MockResourceProvider(
      resources = Set(
        deviceStore.view(),
        deviceStore.viewSelf(),
        entryStore.view(),
        entryStore.viewSelf(),
        entryStore.manage(),
        entryStore.manageSelf()
      )
    )

    lazy implicit val context: RoutesContext = RoutesContext.collect()

    lazy val routes: Route = new DatasetEntries().routes
  }

  private implicit val user: CurrentUser = CurrentUser(User.generateId())

  private val definition = DatasetDefinition.generateId()

  private val userDevice =
    Device(
      id = Device.generateId(),
      name = "test-device",
      node = Node.generateId(),
      owner = user.id,
      active = true,
      limits = None,
      created = Instant.now(),
      updated = Instant.now()
    )

  private val entryCreationDifference = 30.seconds

  private val earliestEntry = DatasetEntry(
    id = DatasetEntry.generateId(),
    definition = definition,
    device = userDevice.id,
    data = Set.empty,
    metadata = Crate.generateId(),
    created = Instant.now().minusSeconds(entryCreationDifference.toSeconds)
  )

  private val latestEntry = DatasetEntry(
    id = DatasetEntry.generateId(),
    definition = definition,
    device = Device.generateId(),
    data = Set.empty,
    metadata = Crate.generateId(),
    created = Instant.now()
  )

  private val entries = Seq(
    earliestEntry,
    latestEntry
  )

  private val createRequest = CreateDatasetEntry(
    definition = definition,
    device = userDevice.id,
    metadata = Crate.generateId(),
    data = Set.empty
  )

  import scala.language.implicitConversions

  private implicit def createRequestToEntity(request: CreateDatasetEntry): RequestEntity =
    Marshal(request).to[RequestEntity].await
}
