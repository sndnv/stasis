package stasis.test.specs.unit.server.api.routes
import java.time.Instant

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{RequestEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import stasis.core.routing.Node
import stasis.server.api.requests.CreateDatasetEntry
import stasis.server.api.responses.{CreatedDatasetEntry, DeletedDatasetEntry}
import stasis.server.api.routes.{DatasetEntries, RoutesContext}
import stasis.server.model.datasets.{DatasetDefinition, DatasetEntry, DatasetEntryStore}
import stasis.server.model.devices.{Device, DeviceStore}
import stasis.server.model.users.User
import stasis.server.security.{CurrentUser, ResourceProvider}
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.server.model.mocks.{MockDatasetEntryStore, MockDeviceStore}
import stasis.test.specs.unit.server.security.mocks.MockResourceProvider

import scala.concurrent.Future

class DatasetEntriesSpec extends AsyncUnitSpec with ScalatestRouteTest {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.server.api.Formats._

  import scala.language.implicitConversions

  private implicit val untypedSystem: ActorSystem = ActorSystem(name = "DatasetEntriesSpec")
  private implicit val log: LoggingAdapter = Logging(untypedSystem, this.getClass.getName)

  private trait TestFixtures {
    lazy val deviceStore: DeviceStore = new MockDeviceStore()

    lazy val entryStore: DatasetEntryStore = new MockDatasetEntryStore()

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

    lazy val routes: Route = DatasetEntries()
  }

  private implicit val user: CurrentUser = CurrentUser(User.generateId())

  private val definition = DatasetDefinition.generateId()

  private val userDevice =
    Device(
      id = Device.generateId(),
      node = Node.generateId(),
      owner = user.id,
      isActive = true,
      limits = None
    )

  private val entries = Seq(
    DatasetEntry(
      id = DatasetEntry.generateId(),
      definition = definition,
      device = userDevice.id,
      data = Set.empty,
      created = Instant.now()
    ),
    DatasetEntry(
      id = DatasetEntry.generateId(),
      definition = definition,
      device = Device.generateId(),
      data = Set.empty,
      created = Instant.now()
    )
  )

  private val createRequest = CreateDatasetEntry(
    definition = definition,
    device = userDevice.id,
    data = Set.empty
  )

  private implicit def createRequestToEntity(request: CreateDatasetEntry): RequestEntity =
    Marshal(request).to[RequestEntity].await

  "DatasetEntries routes (full permissions)" should "respond with all entries for a definition" in {
    val fixtures = new TestFixtures {}
    Future.sequence(entries.map(fixtures.entryStore.manage().create)).await

    Get(s"/for-definition/$definition") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[DatasetEntry]] should contain theSameElementsAs entries
    }
  }

  they should "respond with existing entries" in {
    val fixtures = new TestFixtures {}

    fixtures.entryStore.manage().create(entries.head).await

    Get(s"/${entries.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DatasetEntry] should be(entries.head)
    }
  }

  they should "fail if an entry is missing" in {
    val fixtures = new TestFixtures {}
    Get(s"/${DatasetEntry.generateId()}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "delete existing entries" in {
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

  they should "not delete missing entries" in {
    val fixtures = new TestFixtures {}

    Delete(s"/${entries.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedDatasetEntry] should be(DeletedDatasetEntry(existing = false))
    }
  }

  "DatasetEntries routes (self permissions)" should "respond with all entries for a definition" in {
    val fixtures = new TestFixtures {}
    Future.sequence(entries.map(fixtures.entryStore.manage().create)).await
    fixtures.deviceStore.manage().create(userDevice).await

    Get(s"/own/for-definition/$definition") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[DatasetEntry]] should contain theSameElementsAs entries.take(1)
    }
  }

  they should "create new entries for a definition" in {
    val fixtures = new TestFixtures {}
    fixtures.deviceStore.manage().create(userDevice).await

    Post(s"/own/for-definition/$definition")
      .withEntity(createRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.entryStore
        .view()
        .get(entityAs[CreatedDatasetEntry].entry)
        .map(_.isDefined should be(true))
    }
  }

  they should "fail to create new entries for an unexpected definition" in {
    val fixtures = new TestFixtures {}
    fixtures.deviceStore.manage().create(userDevice).await

    Post(s"/own/for-definition/${DatasetDefinition.generateId()}")
      .withEntity(createRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  they should "respond with existing entries" in {
    val fixtures = new TestFixtures {}
    fixtures.deviceStore.manage().create(userDevice).await

    fixtures.entryStore.manage().create(entries.head).await

    Get(s"/own/${entries.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DatasetEntry] should be(entries.head)
    }
  }

  they should "fail if an entry is missing" in {
    val fixtures = new TestFixtures {}
    Get(s"/own/${DatasetEntry.generateId()}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "delete existing entries" in {
    val fixtures = new TestFixtures {}
    fixtures.deviceStore.manage().create(userDevice).await
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

  they should "not delete missing entries" in {
    val fixtures = new TestFixtures {}
    fixtures.deviceStore.manage().create(userDevice).await

    Delete(s"/own/${entries.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedDatasetEntry] should be(DeletedDatasetEntry(existing = false))
    }
  }
}
