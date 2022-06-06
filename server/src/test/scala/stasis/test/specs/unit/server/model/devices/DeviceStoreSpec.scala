package stasis.test.specs.unit.server.model.devices

import akka.Done
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import stasis.core.routing.Node
import stasis.core.telemetry.TelemetryContext
import stasis.server.security.CurrentUser
import stasis.shared.model.devices.Device
import stasis.shared.model.users.User
import stasis.shared.security.Permission
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext
import stasis.test.specs.unit.server.model.mocks.MockDeviceStore

import scala.util.control.NonFatal

class DeviceStoreSpec extends AsyncUnitSpec {
  "A DeviceStore" should "provide a view resource (privileged)" in {
    val store = MockDeviceStore()
    store.view().requiredPermission should be(Permission.View.Privileged)
  }

  it should "return existing devices via view resource (privileged)" in {
    val store = MockDeviceStore()

    store.manage().create(mockDevice).await

    store.view().get(mockDevice.id).map(result => result should be(Some(mockDevice)))
  }

  it should "return a list of devices via view resource (privileged)" in {
    val store = MockDeviceStore()

    store.manage().create(mockDevice).await
    store.manage().create(mockDevice.copy(id = Device.generateId())).await
    store.manage().create(mockDevice.copy(id = Device.generateId())).await

    store.view().list().map { result =>
      result.size should be(3)
      result.get(mockDevice.id) should be(Some(mockDevice))
    }
  }

  it should "provide a view resource (self)" in {
    val store = MockDeviceStore()
    store.viewSelf().requiredPermission should be(Permission.View.Self)
  }

  it should "return existing devices for current user via view resource (self)" in {
    val store = MockDeviceStore()

    val ownDevice = mockDevice.copy(owner = self.id)
    store.manage().create(ownDevice).await

    store.viewSelf().get(self, ownDevice.id).map(result => result should be(Some(ownDevice)))
  }

  it should "fail to return existing devices not for current user via view resource (self)" in {
    val store = MockDeviceStore()

    store.manage().create(mockDevice).await

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
    store.manage().create(ownDevice).await
    store.manage().create(mockDevice.copy(id = Device.generateId())).await
    store.manage().create(mockDevice.copy(id = Device.generateId())).await

    store.viewSelf().list(self).map { result =>
      result.size should be(1)
      result.get(ownDevice.id) should be(Some(ownDevice))
    }
  }

  it should "provide management resource (privileged)" in {
    val store = MockDeviceStore()
    store.manage().requiredPermission should be(Permission.Manage.Privileged)
  }

  it should "allow creating devices via management resource (privileged)" in {
    val store = MockDeviceStore()

    for {
      createResult <- store.manage().create(mockDevice)
      getResult <- store.view().get(mockDevice.id)
    } yield {
      createResult should be(Done)
      getResult should be(Some(mockDevice))
    }
  }

  it should "allow updating devices via management resource (privileged)" in {
    val store = MockDeviceStore()

    val updatedActive = false

    for {
      createResult <- store.manage().create(mockDevice)
      getResult <- store.view().get(mockDevice.id)
      updateResult <- store.manage().update(mockDevice.copy(active = updatedActive))
      updatedGetResult <- store.view().get(mockDevice.id)
    } yield {
      createResult should be(Done)
      getResult should be(Some(mockDevice))
      updateResult should be(Done)
      updatedGetResult should be(Some(mockDevice.copy(active = updatedActive)))
    }
  }

  it should "allow deleting devices via management resource (privileged)" in {
    val store = MockDeviceStore()

    for {
      createResult <- store.manage().create(mockDevice)
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
      createResult <- store.manageSelf().create(self, ownDevice)
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
      .create(self, mockDevice)
      .map { response =>
        fail(s"Received unexpected response from store: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Expected to create own [${self.id}] device but device for user [${mockDevice.owner}] provided"
        )
      }
  }

  it should "allow updating devices for current user via management resource (self)" in {
    val store = MockDeviceStore()

    val updatedActive = false

    val ownDevice = mockDevice.copy(owner = self.id)

    for {
      createResult <- store.manageSelf().create(self, ownDevice)
      getResult <- store.viewSelf().get(self, ownDevice.id)
      updateResult <- store.manageSelf().update(self, ownDevice.copy(active = updatedActive))
      updatedGetResult <- store.viewSelf().get(self, ownDevice.id)
    } yield {
      createResult should be(Done)
      getResult should be(Some(ownDevice))
      updateResult should be(Done)
      updatedGetResult should be(Some(ownDevice.copy(active = updatedActive)))
    }
  }

  it should "fail to update devices for another user via management resource (self)" in {
    val store = MockDeviceStore()

    store
      .manageSelf()
      .update(self, mockDevice)
      .map { response =>
        fail(s"Received unexpected response from store: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Expected to update own [${self.id}] device but device for user [${mockDevice.owner}] provided"
        )
      }
  }

  it should "allow deleting devices for current user via management resource (self)" in {
    val store = MockDeviceStore()

    val ownDevice = mockDevice.copy(owner = self.id)

    for {
      createResult <- store.manageSelf().create(self, ownDevice)
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

    store.manage().create(mockDevice).await

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

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "DeviceStoreSpec"
  )

  private implicit val telemetry: TelemetryContext = MockTelemetryContext()

  private val mockDevice = Device(
    id = Device.generateId(),
    name = "test-device",
    node = Node.generateId(),
    owner = User.generateId(),
    active = true,
    limits = None
  )

  private val self = CurrentUser(User.generateId())
}
