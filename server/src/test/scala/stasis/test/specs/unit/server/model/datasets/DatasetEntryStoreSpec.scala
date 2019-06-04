package stasis.test.specs.unit.server.model.datasets

import java.time.Instant

import scala.util.control.NonFatal

import akka.Done
import akka.actor.ActorSystem
import stasis.core.packaging.Crate
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.shared.model.devices.Device
import stasis.shared.security.Permission
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.server.model.mocks.MockDatasetEntryStore

class DatasetEntryStoreSpec extends AsyncUnitSpec {

  private implicit val system: ActorSystem = ActorSystem(name = "DatasetEntryStoreSpec")

  private val ownDevices = Seq(Device.generateId(), Device.generateId())

  private val mockEntry = DatasetEntry(
    id = DatasetEntry.generateId(),
    definition = DatasetDefinition.generateId(),
    device = Device.generateId(),
    data = Set.empty,
    metadata = Crate.generateId(),
    created = Instant.now()
  )

  "A DatasetEntryStore" should "provide a view resource (privileged)" in {
    val store = new MockDatasetEntryStore()
    store.view().requiredPermission should be(Permission.View.Privileged)
  }

  it should "return existing entries via view resource (privileged)" in {
    val store = new MockDatasetEntryStore()

    store.manage().create(mockEntry).await

    store.view().get(mockEntry.id).map(result => result should be(Some(mockEntry)))
  }

  it should "return a list of entries via view resource (privileged)" in {
    val store = new MockDatasetEntryStore()

    store.manage().create(mockEntry).await

    store
      .manage()
      .create(mockEntry.copy(id = DatasetEntry.generateId()))
      .await

    store
      .manage()
      .create(mockEntry.copy(id = DatasetEntry.generateId(), definition = DatasetDefinition.generateId()))
      .await

    store.view().list(mockEntry.definition).map { result =>
      result.size should be(2)
      result.get(mockEntry.id) should be(Some(mockEntry))
    }
  }

  it should "provide a view resource (self)" in {
    val store = new MockDatasetEntryStore()
    store.viewSelf().requiredPermission should be(Permission.View.Self)
  }

  it should "return existing entries for current user via view resource (self)" in {
    val store = new MockDatasetEntryStore()

    val ownDefinition = mockEntry.copy(device = ownDevices.head)
    store.manage().create(ownDefinition).await

    store.viewSelf().get(ownDevices, ownDefinition.id).map(result => result should be(Some(ownDefinition)))
  }

  it should "fail to return existing entries not for current user via view resource (self)" in {
    val store = new MockDatasetEntryStore()

    store.manage().create(mockEntry).await

    store
      .viewSelf()
      .get(ownDevices, mockEntry.id)
      .map { response =>
        fail(s"Received unexpected response from store: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(
            s"Expected to retrieve entry for own device but device [${mockEntry.device}] found"
          )
      }
  }

  it should "fail to return missing entries for current user via view resource (self)" in {
    val store = new MockDatasetEntryStore()

    store.viewSelf().get(ownDevices, mockEntry.id).map(result => result should be(None))
  }

  it should "return a list of entries for current user via view resource (self)" in {
    val store = new MockDatasetEntryStore()

    val ownEntry = mockEntry.copy(device = ownDevices.head)
    store.manage().create(ownEntry).await
    store.manage().create(mockEntry.copy(id = DatasetDefinition.generateId())).await
    store.manage().create(mockEntry.copy(id = DatasetDefinition.generateId())).await

    store.viewSelf().list(ownDevices, ownEntry.definition).map { result =>
      result.size should be(1)
      result.get(ownEntry.id) should be(Some(ownEntry))
    }
  }

  it should "provide management resource (privileged)" in {
    val store = new MockDatasetEntryStore()
    store.manage().requiredPermission should be(Permission.Manage.Privileged)
  }

  it should "allow creating entries via management resource (privileged)" in {
    val store = new MockDatasetEntryStore()

    for {
      createResult <- store.manage().create(mockEntry)
      getResult <- store.view().get(mockEntry.id)
    } yield {
      createResult should be(Done)
      getResult should be(Some(mockEntry))
    }
  }

  it should "allow deleting entries via management resource (privileged)" in {
    val store = new MockDatasetEntryStore()

    for {
      createResult <- store.manage().create(mockEntry)
      getResult <- store.view().get(mockEntry.id)
      deleteResult <- store.manage().delete(mockEntry.id)
      deletedGetResult <- store.view().get(mockEntry.id)
    } yield {
      createResult should be(Done)
      getResult should be(Some(mockEntry))
      deleteResult should be(true)
      deletedGetResult should be(None)
    }
  }

  it should "fail to delete missing entries via management resource (privileged)" in {
    val store = new MockDatasetEntryStore()

    for {
      getResult <- store.view().get(mockEntry.id)
      deleteResult <- store.manage().delete(mockEntry.id)
    } yield {
      getResult should be(None)
      deleteResult should be(false)
    }
  }

  it should "provide management resource (self)" in {
    val store = new MockDatasetEntryStore()
    store.manageSelf().requiredPermission should be(Permission.Manage.Self)
  }

  it should "allow creating entries for current user via management resource (self)" in {
    val store = new MockDatasetEntryStore()

    val ownEntry = mockEntry.copy(device = ownDevices.head)

    for {
      createResult <- store.manageSelf().create(ownDevices, ownEntry)
      getResult <- store.viewSelf().get(ownDevices, ownEntry.id)
    } yield {
      createResult should be(Done)
      getResult should be(Some(ownEntry))
    }
  }

  it should "fail to create entries for another user via management resource (self)" in {
    val store = new MockDatasetEntryStore()

    store
      .manageSelf()
      .create(ownDevices, mockEntry)
      .map { response =>
        fail(s"Received unexpected response from store: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(
            s"Expected to create entry for own device but device [${mockEntry.device}] provided"
          )
      }
  }

  it should "allow deleting entries for current user via management resource (self)" in {
    val store = new MockDatasetEntryStore()

    val ownEntry = mockEntry.copy(device = ownDevices.head)

    for {
      createResult <- store.manageSelf().create(ownDevices, ownEntry)
      getResult <- store.viewSelf().get(ownDevices, ownEntry.id)
      deleteResult <- store.manageSelf().delete(ownDevices, ownEntry.id)
      deletedGetResult <- store.viewSelf().get(ownDevices, ownEntry.id)
    } yield {
      createResult should be(Done)
      getResult should be(Some(ownEntry))
      deleteResult should be(true)
      deletedGetResult should be(None)
    }
  }

  it should "fail to delete entries for another user via management resource (self)" in {
    val store = new MockDatasetEntryStore()

    store.manage().create(mockEntry).await

    store
      .manageSelf()
      .delete(ownDevices, mockEntry.id)
      .map { response =>
        fail(s"Received unexpected response from store: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(
            s"Expected to delete entry for own device but device [${mockEntry.device}] provided"
          )
      }
  }

  it should "fail to delete missing entries for current user via management resource (self)" in {
    val store = new MockDatasetEntryStore()

    for {
      getResult <- store.view().get(mockEntry.id)
      deleteResult <- store.manageSelf().delete(ownDevices, mockEntry.id)
    } yield {
      getResult should be(None)
      deleteResult should be(false)
    }
  }
}
