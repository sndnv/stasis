package stasis.test.specs.unit.server.model.datasets

import akka.Done
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import stasis.core.telemetry.TelemetryContext
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.devices.Device
import stasis.shared.security.Permission
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext
import stasis.test.specs.unit.server.model.mocks.MockDatasetDefinitionStore

import scala.concurrent.duration._
import scala.util.control.NonFatal

class DatasetDefinitionStoreSpec extends AsyncUnitSpec {
  "A DatasetDefinitionStore" should "provide a view resource (privileged)" in {
    val store = MockDatasetDefinitionStore()
    store.view().requiredPermission should be(Permission.View.Privileged)
  }

  it should "return existing definitions via view resource (privileged)" in {
    val store = MockDatasetDefinitionStore()

    store.manage().create(mockDefinition).await

    store.view().get(mockDefinition.id).map(result => result should be(Some(mockDefinition)))
  }

  it should "return a list of definitions via view resource (privileged)" in {
    val store = MockDatasetDefinitionStore()

    store.manage().create(mockDefinition).await
    store.manage().create(mockDefinition.copy(id = DatasetDefinition.generateId())).await
    store.manage().create(mockDefinition.copy(id = DatasetDefinition.generateId())).await

    store.view().list().map { result =>
      result.size should be(3)
      result.get(mockDefinition.id) should be(Some(mockDefinition))
    }
  }

  it should "provide a view resource (self)" in {
    val store = MockDatasetDefinitionStore()
    store.viewSelf().requiredPermission should be(Permission.View.Self)
  }

  it should "return existing definitions for current user via view resource (self)" in {
    val store = MockDatasetDefinitionStore()

    val ownDefinition = mockDefinition.copy(device = ownDevices.head)
    store.manage().create(ownDefinition).await

    store.viewSelf().get(ownDevices, ownDefinition.id).map(result => result should be(Some(ownDefinition)))
  }

  it should "fail to return existing definitions not for current user via view resource (self)" in {
    val store = MockDatasetDefinitionStore()

    store.manage().create(mockDefinition).await

    store
      .viewSelf()
      .get(ownDevices, mockDefinition.id)
      .map { response =>
        fail(s"Received unexpected response from store: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Expected to retrieve definition for own device but device [${mockDefinition.device}] found"
        )
      }
  }

  it should "fail to return missing definitions for current user via view resource (self)" in {
    val store = MockDatasetDefinitionStore()

    store.viewSelf().get(ownDevices, mockDefinition.id).map(result => result should be(None))
  }

  it should "return a list of definitions for current user via view resource (self)" in {
    val store = MockDatasetDefinitionStore()

    val ownDefinition = mockDefinition.copy(device = ownDevices.head)
    store.manage().create(ownDefinition).await
    store.manage().create(mockDefinition.copy(id = DatasetDefinition.generateId())).await
    store.manage().create(mockDefinition.copy(id = DatasetDefinition.generateId())).await

    store.viewSelf().list(ownDevices).map { result =>
      result.size should be(1)
      result.get(ownDefinition.id) should be(Some(ownDefinition))
    }
  }

  it should "provide management resource (privileged)" in {
    val store = MockDatasetDefinitionStore()
    store.manage().requiredPermission should be(Permission.Manage.Privileged)
  }

  it should "allow creating definitions via management resource (privileged)" in {
    val store = MockDatasetDefinitionStore()

    for {
      createResult <- store.manage().create(mockDefinition)
      getResult <- store.view().get(mockDefinition.id)
    } yield {
      createResult should be(Done)
      getResult should be(Some(mockDefinition))
    }
  }

  it should "allow updating definitions via management resource (privileged)" in {
    val store = MockDatasetDefinitionStore()

    val updatedCopies = 42

    for {
      createResult <- store.manage().create(mockDefinition)
      getResult <- store.view().get(mockDefinition.id)
      updateResult <- store.manage().update(mockDefinition.copy(redundantCopies = updatedCopies))
      updatedGetResult <- store.view().get(mockDefinition.id)
    } yield {
      createResult should be(Done)
      getResult should be(Some(mockDefinition))
      updateResult should be(Done)
      updatedGetResult should be(Some(mockDefinition.copy(redundantCopies = updatedCopies)))
    }
  }

  it should "allow deleting definitions via management resource (privileged)" in {
    val store = MockDatasetDefinitionStore()

    for {
      createResult <- store.manage().create(mockDefinition)
      getResult <- store.view().get(mockDefinition.id)
      deleteResult <- store.manage().delete(mockDefinition.id)
      deletedGetResult <- store.view().get(mockDefinition.id)
    } yield {
      createResult should be(Done)
      getResult should be(Some(mockDefinition))
      deleteResult should be(true)
      deletedGetResult should be(None)
    }
  }

  it should "fail to delete missing definitions via management resource (privileged)" in {
    val store = MockDatasetDefinitionStore()

    for {
      getResult <- store.view().get(mockDefinition.id)
      deleteResult <- store.manage().delete(mockDefinition.id)
    } yield {
      getResult should be(None)
      deleteResult should be(false)
    }
  }

  it should "provide management resource (self)" in {
    val store = MockDatasetDefinitionStore()
    store.manageSelf().requiredPermission should be(Permission.Manage.Self)
  }

  it should "allow creating definitions for current user via management resource (self)" in {
    val store = MockDatasetDefinitionStore()

    val ownDefinition = mockDefinition.copy(device = ownDevices.head)

    for {
      createResult <- store.manageSelf().create(ownDevices, ownDefinition)
      getResult <- store.viewSelf().get(ownDevices, ownDefinition.id)
    } yield {
      createResult should be(Done)
      getResult should be(Some(ownDefinition))
    }
  }

  it should "fail to create definitions for another user via management resource (self)" in {
    val store = MockDatasetDefinitionStore()

    store
      .manageSelf()
      .create(ownDevices, mockDefinition)
      .map { response =>
        fail(s"Received unexpected response from store: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Expected to create definition for own device but device [${mockDefinition.device}] provided"
        )
      }
  }

  it should "allow updating definitions for current user via management resource (self)" in {
    val store = MockDatasetDefinitionStore()

    val updatedCopies = 42

    val ownDefinition = mockDefinition.copy(device = ownDevices.head)

    for {
      createResult <- store.manageSelf().create(ownDevices, ownDefinition)
      getResult <- store.viewSelf().get(ownDevices, ownDefinition.id)
      updateResult <- store.manageSelf().update(ownDevices, ownDefinition.copy(redundantCopies = updatedCopies))
      updatedGetResult <- store.viewSelf().get(ownDevices, ownDefinition.id)
    } yield {
      createResult should be(Done)
      getResult should be(Some(ownDefinition))
      updateResult should be(Done)
      updatedGetResult should be(Some(ownDefinition.copy(redundantCopies = updatedCopies)))
    }
  }

  it should "fail to update definitions for another user via management resource (self)" in {
    val store = MockDatasetDefinitionStore()

    store
      .manageSelf()
      .update(ownDevices, mockDefinition)
      .map { response =>
        fail(s"Received unexpected response from store: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Expected to update definition for own device but device [${mockDefinition.device}] provided"
        )
      }
  }

  it should "allow deleting definitions for current user via management resource (self)" in {
    val store = MockDatasetDefinitionStore()

    val ownDefinition = mockDefinition.copy(device = ownDevices.head)

    for {
      createResult <- store.manageSelf().create(ownDevices, ownDefinition)
      getResult <- store.viewSelf().get(ownDevices, ownDefinition.id)
      deleteResult <- store.manageSelf().delete(ownDevices, ownDefinition.id)
      deletedGetResult <- store.viewSelf().get(ownDevices, ownDefinition.id)
    } yield {
      createResult should be(Done)
      getResult should be(Some(ownDefinition))
      deleteResult should be(true)
      deletedGetResult should be(None)
    }
  }

  it should "fail to delete definitions for another user via management resource (self)" in {
    val store = MockDatasetDefinitionStore()

    store.manage().create(mockDefinition).await

    store
      .manageSelf()
      .delete(ownDevices, mockDefinition.id)
      .map { response =>
        fail(s"Received unexpected response from store: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Expected to delete definition for own device but device [${mockDefinition.device}] provided"
        )
      }
  }

  it should "fail to delete missing definitions for current user via management resource (self)" in {
    val store = MockDatasetDefinitionStore()

    for {
      getResult <- store.view().get(mockDefinition.id)
      deleteResult <- store.manageSelf().delete(ownDevices, mockDefinition.id)
    } yield {
      getResult should be(None)
      deleteResult should be(false)
    }
  }

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "DatasetDefinitionStoreSpec"
  )

  private implicit val telemetry: TelemetryContext = MockTelemetryContext()

  private val ownDevices = Seq(Device.generateId(), Device.generateId())

  private val mockDefinition = DatasetDefinition(
    id = DatasetDefinition.generateId(),
    info = "test-definition",
    device = Device.generateId(),
    redundantCopies = 1,
    existingVersions = DatasetDefinition.Retention(DatasetDefinition.Retention.Policy.LatestOnly, 1.second),
    removedVersions = DatasetDefinition.Retention(DatasetDefinition.Retention.Policy.LatestOnly, 1.second)
  )
}
