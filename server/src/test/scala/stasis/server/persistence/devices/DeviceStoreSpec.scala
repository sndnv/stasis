package stasis.server.persistence.devices

import java.time.Instant

import scala.util.control.NonFatal

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.core.routing.Node
import stasis.server.security.CurrentUser
import stasis.shared.model.devices.Device
import stasis.shared.model.users.User
import stasis.shared.security.Permission
import stasis.test.specs.unit.AsyncUnitSpec

class DeviceStoreSpec extends AsyncUnitSpec {
  "A DeviceStore" should "provide a view resource (privileged)" in {
    val store = MockDeviceStore()
    store.view().requiredPermission should be(Permission.View.Privileged)
  }

  it should "return existing devices via view resource (privileged)" in {
    val store = MockDeviceStore()

    store.manage().put(mockDevice).await

    store.view().get(mockDevice.id).map(result => result should be(Some(mockDevice)))
  }

  it should "return a list of devices via view resource (privileged)" in {
    val store = MockDeviceStore()

    store.manage().put(mockDevice).await
    store.manage().put(mockDevice.copy(id = Device.generateId())).await
    store.manage().put(mockDevice.copy(id = Device.generateId())).await

    store.view().list().map { result =>
      result.size should be(3)
      result.find(_.id == mockDevice.id) should be(Some(mockDevice))
    }
  }

  it should "provide a view resource (self)" in {
    val store = MockDeviceStore()
    store.viewSelf().requiredPermission should be(Permission.View.Self)
  }

  it should "return existing devices for current user via view resource (self)" in {
    val store = MockDeviceStore()

    val ownDevice = mockDevice.copy(owner = self.id)
    store.manage().put(ownDevice).await

    store.viewSelf().get(self, ownDevice.id).map(result => result should be(Some(ownDevice)))
  }

  it should "fail to return existing devices not for current user via view resource (self)" in {
    val store = MockDeviceStore()

    store.manage().put(mockDevice).await

    store
      .viewSelf()
      .get(self, mockDevice.id)
      .map { response =>
        fail(s"Received unexpected response from store: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Expected to retrieve own [${self.id}] device but device for user [${mockDevice.owner}] found"
        )
      }
  }

  it should "fail to return missing devices for current user via view resource (self)" in {
    val store = MockDeviceStore()

    store.viewSelf().get(self, mockDevice.id).map(result => result should be(None))
  }

  it should "return a list of devices for current user via view resource (self)" in {
    val store = MockDeviceStore()

    val ownDevice = mockDevice.copy(owner = self.id)
    store.manage().put(ownDevice).await
    store.manage().put(mockDevice.copy(id = Device.generateId())).await
    store.manage().put(mockDevice.copy(id = Device.generateId())).await

    store.viewSelf().list(self).map { result =>
      result.size should be(1)
      result.find(_.id == ownDevice.id) should be(Some(ownDevice))
    }
  }

  it should "provide management resource (privileged)" in {
    val store = MockDeviceStore()
    store.manage().requiredPermission should be(Permission.Manage.Privileged)
  }

  it should "allow creating devices via management resource (privileged)" in {
    val store = MockDeviceStore()

    for {
      createResult <- store.manage().put(mockDevice)
      getResult <- store.view().get(mockDevice.id)
    } yield {
      createResult should be(Done)
      getResult should be(Some(mockDevice))
    }
  }

  it should "allow deleting devices via management resource (privileged)" in {
    val store = MockDeviceStore()

    for {
      createResult <- store.manage().put(mockDevice)
      getResult <- store.view().get(mockDevice.id)
      deleteResult <- store.manage().delete(mockDevice.id)
      deletedGetResult <- store.view().get(mockDevice.id)
    } yield {
      createResult should be(Done)
      getResult should be(Some(mockDevice))
      deleteResult should be(true)
      deletedGetResult should be(None)
    }
  }

  it should "fail to delete missing devices via management resource (privileged)" in {
    val store = MockDeviceStore()

    for {
      getResult <- store.view().get(mockDevice.id)
      deleteResult <- store.manage().delete(mockDevice.id)
    } yield {
      getResult should be(None)
      deleteResult should be(false)
    }
  }

  it should "provide management resource (self)" in {
    val store = MockDeviceStore()
    store.manageSelf().requiredPermission should be(Permission.Manage.Self)
  }

  it should "allow creating devices for current user via management resource (self)" in {
    val store = MockDeviceStore()

    val ownDevice = mockDevice.copy(owner = self.id)

    for {
      createResult <- store.manageSelf().put(self, ownDevice)
      getResult <- store.viewSelf().get(self, ownDevice.id)
    } yield {
      createResult should be(Done)
      getResult should be(Some(ownDevice))
    }
  }

  it should "fail to create devices for another user via management resource (self)" in {
    val store = MockDeviceStore()

    store
      .manageSelf()
      .put(self, mockDevice)
      .map { response =>
        fail(s"Received unexpected response from store: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Expected to put own [${self.id}] device but device for user [${mockDevice.owner}] provided"
        )
      }
  }

  it should "allow deleting devices for current user via management resource (self)" in {
    val store = MockDeviceStore()

    val ownDevice = mockDevice.copy(owner = self.id)

    for {
      createResult <- store.manageSelf().put(self, ownDevice)
      getResult <- store.viewSelf().get(self, ownDevice.id)
      deleteResult <- store.manageSelf().delete(self, ownDevice.id)
      deletedGetResult <- store.viewSelf().get(self, ownDevice.id)
    } yield {
      createResult should be(Done)
      getResult should be(Some(ownDevice))
      deleteResult should be(true)
      deletedGetResult should be(None)
    }
  }

  it should "fail to delete devices for another user via management resource (self)" in {
    val store = MockDeviceStore()

    store.manage().put(mockDevice).await

    store
      .manageSelf()
      .delete(self, mockDevice.id)
      .map { response =>
        fail(s"Received unexpected response from store: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Expected to delete own [${self.id}] device but device for user [${mockDevice.owner}] provided"
        )
      }
  }

  it should "fail to delete missing devices for current user via management resource (self)" in {
    val store = MockDeviceStore()

    for {
      getResult <- store.view().get(mockDevice.id)
      deleteResult <- store.manageSelf().delete(self, mockDevice.id)
    } yield {
      getResult should be(None)
      deleteResult should be(false)
    }
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "DeviceStoreSpec"
  )

  private val mockDevice = Device(
    id = Device.generateId(),
    name = "test-device",
    node = Node.generateId(),
    owner = User.generateId(),
    active = true,
    limits = None,
    created = Instant.now(),
    updated = Instant.now()
  )

  private val self = CurrentUser(User.generateId())
}
