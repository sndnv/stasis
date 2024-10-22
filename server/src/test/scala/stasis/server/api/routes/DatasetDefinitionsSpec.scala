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

import stasis.core.routing.Node
import stasis.server.persistence.datasets.DatasetDefinitionStore
import stasis.server.persistence.datasets.MockDatasetDefinitionStore
import stasis.server.persistence.devices.DeviceStore
import stasis.server.persistence.devices.MockDeviceStore
import stasis.server.security.CurrentUser
import stasis.server.security.ResourceProvider
import stasis.server.security.mocks.MockResourceProvider
import stasis.shared.api.requests.CreateDatasetDefinition
import stasis.shared.api.requests.UpdateDatasetDefinition
import stasis.shared.api.responses.CreatedDatasetDefinition
import stasis.shared.api.responses.DeletedDatasetDefinition
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.devices.Device
import stasis.shared.model.users.User
import stasis.test.specs.unit.AsyncUnitSpec

class DatasetDefinitionsSpec extends AsyncUnitSpec with ScalatestRouteTest {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  import stasis.shared.api.Formats._

  "DatasetDefinitions routes (full permissions)" should "respond with all definitions" in withRetry {
    val fixtures = new TestFixtures {}
    Future.sequence(definitions.map(fixtures.definitionStore.manage().put)).await

    Get("/") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[DatasetDefinition]] should contain theSameElementsAs definitions
    }
  }

  they should "create new definitions" in withRetry {
    val fixtures = new TestFixtures {}
    Post("/").withEntity(createRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.definitionStore
        .view()
        .get(entityAs[CreatedDatasetDefinition].definition)
        .map(_.isDefined should be(true))
    }
  }

  they should "respond with existing definitions" in withRetry {
    val fixtures = new TestFixtures {}

    fixtures.definitionStore.manage().put(definitions.head).await

    Get(s"/${definitions.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DatasetDefinition] should be(definitions.head)
    }
  }

  they should "fail if a definition is missing" in withRetry {
    val fixtures = new TestFixtures {}
    Get(s"/${DatasetDefinition.generateId()}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "update existing definitions" in withRetry {
    val fixtures = new TestFixtures {}
    fixtures.definitionStore.manage().put(definitions.head).await
    val updatedCopies = 42

    Put(s"/${definitions.head.id}")
      .withEntity(updateRequest.copy(redundantCopies = updatedCopies)) ~> fixtures.routes ~> check {
      fixtures.definitionStore
        .view()
        .get(definitions.head.id)
        .map(_.map(_.redundantCopies) should be(Some(updatedCopies)))
    }
  }

  they should "fail to update if a definition is missing" in withRetry {
    val fixtures = new TestFixtures {}
    Put(s"/${DatasetDefinition.generateId()}")
      .withEntity(updateRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  they should "delete existing definitions" in withRetry {
    val fixtures = new TestFixtures {}
    fixtures.definitionStore.manage().put(definitions.head).await

    Delete(s"/${definitions.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedDatasetDefinition] should be(DeletedDatasetDefinition(existing = true))

      fixtures.definitionStore
        .view()
        .get(definitions.head.id)
        .map(_ should be(None))
    }
  }

  they should "not delete missing definitions" in withRetry {
    val fixtures = new TestFixtures {}

    Delete(s"/${definitions.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedDatasetDefinition] should be(DeletedDatasetDefinition(existing = false))
    }
  }

  "DatasetDefinitions routes (self permissions)" should "respond with all definitions" in withRetry {
    val fixtures = new TestFixtures {}
    Future.sequence(definitions.map(fixtures.definitionStore.manage().put)).await
    fixtures.deviceStore.manage().put(userDevice).await

    Get("/own") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[DatasetDefinition]] should contain theSameElementsAs definitions.take(1)
    }
  }

  they should "create new definitions" in withRetry {
    val fixtures = new TestFixtures {}
    fixtures.deviceStore.manage().put(userDevice).await

    Post("/own")
      .withEntity(createRequest.copy(device = userDevice.id)) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.definitionStore
        .view()
        .get(entityAs[CreatedDatasetDefinition].definition)
        .map(_.isDefined should be(true))
    }
  }

  they should "respond with existing definitions" in withRetry {
    val fixtures = new TestFixtures {}
    fixtures.deviceStore.manage().put(userDevice).await

    fixtures.definitionStore.manage().put(definitions.head).await

    Get(s"/own/${definitions.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DatasetDefinition] should be(definitions.head)
    }
  }

  they should "fail if a definition is missing" in withRetry {
    val fixtures = new TestFixtures {}
    Get(s"/own/${DatasetDefinition.generateId()}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "update existing definitions" in withRetry {
    val fixtures = new TestFixtures {}
    fixtures.deviceStore.manage().put(userDevice).await
    fixtures.definitionStore.manage().put(definitions.head.copy(device = userDevice.id)).await
    val updatedCopies = 42

    Put(s"/own/${definitions.head.id}")
      .withEntity(updateRequest.copy(redundantCopies = updatedCopies)) ~> fixtures.routes ~> check {
      fixtures.definitionStore
        .view()
        .get(definitions.head.id)
        .map(_.map(_.redundantCopies) should be(Some(updatedCopies)))
    }
  }

  they should "fail to update if a definition is missing" in withRetry {
    val fixtures = new TestFixtures {}
    Put(s"/own/${DatasetDefinition.generateId()}")
      .withEntity(updateRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  they should "delete existing definitions" in withRetry {
    val fixtures = new TestFixtures {}
    fixtures.deviceStore.manage().put(userDevice).await
    fixtures.definitionStore.manage().put(definitions.head.copy(device = userDevice.id)).await

    Delete(s"/own/${definitions.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedDatasetDefinition] should be(DeletedDatasetDefinition(existing = true))

      fixtures.definitionStore
        .view()
        .get(definitions.head.id)
        .map(_ should be(None))
    }
  }

  they should "not delete missing definitions" in withRetry {
    val fixtures = new TestFixtures {}
    fixtures.deviceStore.manage().put(userDevice).await

    Delete(s"/own/${definitions.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedDatasetDefinition] should be(DeletedDatasetDefinition(existing = false))
    }
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "DatasetDefinitionsSpec"
  )

  private implicit val untypedSystem: org.apache.pekko.actor.ActorSystem = typedSystem.classicSystem

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private trait TestFixtures {
    lazy val deviceStore: DeviceStore = MockDeviceStore()

    lazy val definitionStore: DatasetDefinitionStore = MockDatasetDefinitionStore()

    implicit lazy val provider: ResourceProvider = new MockResourceProvider(
      resources = Set(
        deviceStore.view(),
        deviceStore.viewSelf(),
        definitionStore.view(),
        definitionStore.viewSelf(),
        definitionStore.manage(),
        definitionStore.manageSelf()
      )
    )

    lazy implicit val context: RoutesContext = RoutesContext.collect()

    lazy val routes: Route = new DatasetDefinitions().routes
  }

  private implicit val user: CurrentUser = CurrentUser(User.generateId())

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

  private val definitions = Seq(
    DatasetDefinition(
      id = DatasetDefinition.generateId(),
      info = "test-definition-01",
      device = userDevice.id,
      redundantCopies = 1,
      existingVersions = DatasetDefinition.Retention(
        DatasetDefinition.Retention.Policy.All,
        duration = 1.second
      ),
      removedVersions = DatasetDefinition.Retention(
        DatasetDefinition.Retention.Policy.LatestOnly,
        duration = 1.second
      ),
      created = Instant.now(),
      updated = Instant.now()
    ),
    DatasetDefinition(
      id = DatasetDefinition.generateId(),
      info = "test-definition-02",
      device = Device.generateId(),
      redundantCopies = 2,
      existingVersions = DatasetDefinition.Retention(
        DatasetDefinition.Retention.Policy.AtMost(versions = 5),
        duration = 1.second
      ),
      removedVersions = DatasetDefinition.Retention(
        DatasetDefinition.Retention.Policy.LatestOnly,
        duration = 1.second
      ),
      created = Instant.now(),
      updated = Instant.now()
    )
  )

  private val createRequest = CreateDatasetDefinition(
    info = "new-test-definition",
    device = Device.generateId(),
    redundantCopies = 1,
    existingVersions = DatasetDefinition.Retention(DatasetDefinition.Retention.Policy.All, duration = 1.second),
    removedVersions = DatasetDefinition.Retention(DatasetDefinition.Retention.Policy.All, duration = 1.second)
  )

  private val updateRequest = UpdateDatasetDefinition(
    info = "updated-test-definition",
    redundantCopies = 1,
    existingVersions = DatasetDefinition.Retention(DatasetDefinition.Retention.Policy.All, duration = 1.second),
    removedVersions = DatasetDefinition.Retention(DatasetDefinition.Retention.Policy.All, duration = 1.second)
  )

  import scala.language.implicitConversions

  private implicit def createRequestToEntity(request: CreateDatasetDefinition): RequestEntity =
    Marshal(request).to[RequestEntity].await

  private implicit def updateRequestToEntity(request: UpdateDatasetDefinition): RequestEntity =
    Marshal(request).to[RequestEntity].await

}
