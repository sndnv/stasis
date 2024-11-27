package stasis.server.persistence.devices

import java.time.Instant

import scala.util.control.NonFatal

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.layers.telemetry.TelemetryContext
import stasis.server.security.CurrentUser
import stasis.shared.api.requests.CreateDeviceOwn
import stasis.shared.model.devices.Device
import stasis.shared.model.devices.DeviceBootstrapCode
import stasis.shared.model.users.User
import stasis.shared.security.Permission
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class DeviceBootstrapCodeStoreSpec extends AsyncUnitSpec {
  "A DeviceBootstrapCodeStore" should "provide a view resource (privileged)" in {
    val store = MockDeviceBootstrapCodeStore()
    store.view().requiredPermission should be(Permission.View.Privileged)
  }

  it should "return existing device bootstrap codes via view resource (privileged)" in {
    val store = MockDeviceBootstrapCodeStore()

    store.manage().put(mockBootstrapCode).await

    store.view().get(mockBootstrapCode.value).map(result => result should be(Some(mockBootstrapCode)))
  }

  it should "return a (sanitized) list of device bootstrap codes via view resource (privileged)" in {
    val store = MockDeviceBootstrapCodeStore()

    store.manage().put(mockBootstrapCode).await
    store.manage().put(mockBootstrapCode.copy(value = "test-code-2")).await
    store.manage().put(mockBootstrapCode.copy(value = "test-code-3")).await

    store.view().list().map { result =>
      result.size should be(3)
      result.headOption should be(Some(mockBootstrapCode.copy(value = "*****")))
    }
  }

  it should "provide a view resource (self)" in {
    val store = MockDeviceBootstrapCodeStore()
    store.viewSelf().requiredPermission should be(Permission.View.Self)
  }

  it should "return existing device bootstrap codes for current user via view resource (self)" in {
    val store = MockDeviceBootstrapCodeStore()

    val ownBootstrapCode = mockBootstrapCode.copy(owner = self.id, target = Left(ownDevices.head))
    store.manage().put(ownBootstrapCode).await

    store.viewSelf().get(self, ownBootstrapCode.value).map(result => result should be(Some(ownBootstrapCode)))
  }

  it should "fail to return existing device bootstrap codes not for current user via view resource (self)" in {
    val store = MockDeviceBootstrapCodeStore()

    store.manage().put(mockBootstrapCode).await

    store
      .viewSelf()
      .get(self, mockBootstrapCode.value)
      .map { response =>
        fail(s"Received unexpected response from store: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Expected to retrieve own device bootstrap code but code for user [${mockBootstrapCode.owner}] found"
        )
      }
  }

  it should "fail to return missing device bootstrap codes for current user via view resource (self)" in {
    val store = MockDeviceBootstrapCodeStore()

    store.viewSelf().get(self, mockBootstrapCode.value).map(result => result should be(None))
  }

  it should "return a list of device bootstrap codes for current user via view resource (self)" in {
    val store = MockDeviceBootstrapCodeStore()

    val ownBootstrapCode = mockBootstrapCode.copy(owner = self.id, target = Left(ownDevices.head))
    store.manage().put(ownBootstrapCode).await
    store.manage().put(mockBootstrapCode.copy(value = "test-code-2")).await
    store.manage().put(mockBootstrapCode.copy(value = "test-code-3")).await

    store.viewSelf().list(self).map { result =>
      result.size should be(1)
      result.headOption should be(Some(ownBootstrapCode.copy(value = "*****")))
    }
  }

  it should "provide management resource (privileged)" in {
    val store = MockDeviceBootstrapCodeStore()
    store.manage().requiredPermission should be(Permission.Manage.Privileged)
  }

  it should "allow creating device bootstrap codes via management resource (privileged)" in {
    val store = MockDeviceBootstrapCodeStore()

    for {
      putResult <- store.manage().put(mockBootstrapCode)
      getResult <- store.view().get(mockBootstrapCode.value)
    } yield {
      putResult should be(Done)
      getResult should be(Some(mockBootstrapCode))
    }
  }

  it should "allow deleting device bootstrap codes via management resource (privileged)" in {
    val store = MockDeviceBootstrapCodeStore()

    for {
      putResult <- store.manage().put(mockBootstrapCode)
      getResult <- store.view().get(mockBootstrapCode.value)
      deleteResult <- store.manage().delete(mockBootstrapCode.id)
      deletedGetResult <- store.view().get(mockBootstrapCode.value)
    } yield {
      putResult should be(Done)
      getResult should be(Some(mockBootstrapCode))
      deleteResult should be(true)
      deletedGetResult should be(None)
    }
  }

  it should "fail to delete missing device bootstrap codes via management resource (privileged)" in {
    val store = MockDeviceBootstrapCodeStore()

    for {
      getResult <- store.view().get(mockBootstrapCode.value)
      deleteResult <- store.manage().delete(mockBootstrapCode.id)
    } yield {
      getResult should be(None)
      deleteResult should be(false)
    }
  }

  it should "allow consuming device bootstrap codes via management resource (privileged)" in {
    val store = MockDeviceBootstrapCodeStore()

    for {
      putResult <- store.manage().put(mockBootstrapCode)
      getResult <- store.view().get(mockBootstrapCode.value)
      consumeResult <- store.manage().consume(mockBootstrapCode.value)
      consumedGetResult <- store.view().get(mockBootstrapCode.value)
    } yield {
      putResult should be(Done)
      getResult should be(Some(mockBootstrapCode))
      consumeResult should be(Some(mockBootstrapCode))
      consumedGetResult should be(None)
    }
  }

  it should "provide management resource (self)" in {
    val store = MockDeviceBootstrapCodeStore()
    store.manageSelf().requiredPermission should be(Permission.Manage.Self)
  }

  it should "allow creating device bootstrap codes for current user via management resource (self, existing device)" in {
    val store = MockDeviceBootstrapCodeStore()

    val ownBootstrapCode = mockBootstrapCode.copy(owner = self.id, target = Left(ownDevices.head))

    for {
      putResult <- store.manageSelf().put(self, ownBootstrapCode)
      getResult <- store.viewSelf().get(self, ownBootstrapCode.value)
    } yield {
      putResult should be(Done)
      getResult should be(Some(ownBootstrapCode))
    }
  }

  it should "allow creating device bootstrap codes for current user via management resource (self, new device)" in {
    val store = MockDeviceBootstrapCodeStore()

    val request = CreateDeviceOwn(name = "test-device", limits = None)
    val ownBootstrapCode = mockBootstrapCode.copy(owner = self.id, target = Right(request))

    for {
      putResult <- store.manageSelf().put(self, ownBootstrapCode)
      getResult <- store.viewSelf().get(self, ownBootstrapCode.value)
    } yield {
      putResult should be(Done)
      getResult should be(Some(ownBootstrapCode))
    }
  }

  it should "fail to create device bootstrap codes for another user via management resource (self)" in {
    val store = MockDeviceBootstrapCodeStore()

    store
      .manageSelf()
      .put(self, mockBootstrapCode)
      .map { response =>
        fail(s"Received unexpected response from store: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Expected to put own device bootstrap code but code for user [${mockBootstrapCode.owner}] provided"
        )
      }
  }

  it should "allow deleting device bootstrap codes for current user via management resource (self)" in {
    val store = MockDeviceBootstrapCodeStore()

    val ownBootstrapCode = mockBootstrapCode.copy(owner = self.id, target = Left(ownDevices.head))

    for {
      putResult <- store.manageSelf().put(self, ownBootstrapCode)
      getResult <- store.viewSelf().get(self, ownBootstrapCode.value)
      deleteResult <- store.manageSelf().delete(self, ownBootstrapCode.id)
      deletedGetResult <- store.viewSelf().get(self, ownBootstrapCode.value)
    } yield {
      putResult should be(Done)
      getResult should be(Some(ownBootstrapCode))
      deleteResult should be(true)
      deletedGetResult should be(None)
    }
  }

  it should "fail to delete device bootstrap codes for another user via management resource (self)" in {
    val store = MockDeviceBootstrapCodeStore()

    store.manage().put(mockBootstrapCode).await

    store
      .manageSelf()
      .delete(self, mockBootstrapCode.id)
      .map { response =>
        fail(s"Received unexpected response from store: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Expected to delete own device bootstrap code but code for user [${mockBootstrapCode.owner}] found"
        )
      }
  }

  it should "fail to delete missing device bootstrap codes for current user via management resource (self)" in {
    val store = MockDeviceBootstrapCodeStore()

    val ownBootstrapCode = mockBootstrapCode.copy(owner = self.id, target = Left(ownDevices.head))

    for {
      getResult <- store.view().get(ownBootstrapCode.value)
      deleteResult <- store.manageSelf().delete(self, ownBootstrapCode.id)
    } yield {
      getResult should be(None)
      deleteResult should be(false)
    }
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "DeviceBootstrapCodeStore"
  )

  private implicit val telemetry: TelemetryContext = MockTelemetryContext()

  private val self = CurrentUser(User.generateId())

  private val ownDevices = Seq(Device.generateId(), Device.generateId())

  private val mockBootstrapCode = DeviceBootstrapCode(
    value = "test-code",
    owner = User.generateId(),
    device = Device.generateId(),
    expiresAt = Instant.now().plusSeconds(42)
  )
}
