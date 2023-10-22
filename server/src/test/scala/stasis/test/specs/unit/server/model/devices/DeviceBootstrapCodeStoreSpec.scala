package stasis.test.specs.unit.server.model.devices

import java.time.Instant
import org.apache.pekko.Done
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import stasis.core.telemetry.TelemetryContext
import stasis.shared.model.devices.{Device, DeviceBootstrapCode}
import stasis.shared.model.users.User
import stasis.shared.security.Permission
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext
import stasis.test.specs.unit.server.model.mocks.MockDeviceBootstrapCodeStore

import scala.util.control.NonFatal

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

    val ownBootstrapCode = mockBootstrapCode.copy(device = ownDevices.head)
    store.manage().put(ownBootstrapCode).await

    store.viewSelf().get(ownDevices, ownBootstrapCode.value).map(result => result should be(Some(ownBootstrapCode)))
  }

  it should "fail to return existing device bootstrap codes not for current user via view resource (self)" in {
    val store = MockDeviceBootstrapCodeStore()

    store.manage().put(mockBootstrapCode).await

    store
      .viewSelf()
      .get(ownDevices, mockBootstrapCode.value)
      .map { response =>
        fail(s"Received unexpected response from store: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Expected to retrieve own device bootstrap code but code for device [${mockBootstrapCode.device}] found"
        )
      }
  }

  it should "fail to return missing device bootstrap codes for current user via view resource (self)" in {
    val store = MockDeviceBootstrapCodeStore()

    store.viewSelf().get(ownDevices, mockBootstrapCode.value).map(result => result should be(None))
  }

  it should "return a list of device bootstrap codes for current user via view resource (self)" in {
    val store = MockDeviceBootstrapCodeStore()

    val ownBootstrapCode = mockBootstrapCode.copy(device = ownDevices.head)
    store.manage().put(ownBootstrapCode).await
    store.manage().put(mockBootstrapCode.copy(value = "test-code-2")).await
    store.manage().put(mockBootstrapCode.copy(value = "test-code-3")).await

    store.viewSelf().list(ownDevices).map { result =>
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
      deleteResult <- store.manage().delete(mockBootstrapCode.device)
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
      deleteResult <- store.manage().delete(mockBootstrapCode.device)
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

  it should "allow creating device bootstrap codes for current user via management resource (self)" in {
    val store = MockDeviceBootstrapCodeStore()

    val ownBootstrapCode = mockBootstrapCode.copy(device = ownDevices.head)

    for {
      putResult <- store.manageSelf().put(ownDevices, ownBootstrapCode)
      getResult <- store.viewSelf().get(ownDevices, ownBootstrapCode.value)
    } yield {
      putResult should be(Done)
      getResult should be(Some(ownBootstrapCode))
    }
  }

  it should "fail to create device bootstrap codes for another user via management resource (self)" in {
    val store = MockDeviceBootstrapCodeStore()

    store
      .manageSelf()
      .put(ownDevices, mockBootstrapCode)
      .map { response =>
        fail(s"Received unexpected response from store: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Expected to put own device bootstrap code but code for device [${mockBootstrapCode.device}] provided"
        )
      }
  }

  it should "allow deleting device bootstrap codes for current user via management resource (self)" in {
    val store = MockDeviceBootstrapCodeStore()

    val ownBootstrapCode = mockBootstrapCode.copy(device = ownDevices.head)

    for {
      putResult <- store.manageSelf().put(ownDevices, ownBootstrapCode)
      getResult <- store.viewSelf().get(ownDevices, ownBootstrapCode.value)
      deleteResult <- store.manageSelf().delete(ownDevices, ownBootstrapCode.device)
      deletedGetResult <- store.viewSelf().get(ownDevices, ownBootstrapCode.value)
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
      .delete(ownDevices, mockBootstrapCode.device)
      .map { response =>
        fail(s"Received unexpected response from store: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Expected to delete own device bootstrap code but code for device [${mockBootstrapCode.device}] provided"
        )
      }
  }

  it should "fail to delete missing device bootstrap codes for current user via management resource (self)" in {
    val store = MockDeviceBootstrapCodeStore()

    val ownBootstrapCode = mockBootstrapCode.copy(device = ownDevices.head)

    for {
      getResult <- store.view().get(ownBootstrapCode.value)
      deleteResult <- store.manageSelf().delete(ownDevices, ownBootstrapCode.device)
    } yield {
      getResult should be(None)
      deleteResult should be(false)
    }
  }

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "DeviceBootstrapCodeStore"
  )

  private implicit val telemetry: TelemetryContext = MockTelemetryContext()

  private val ownDevices = Seq(Device.generateId(), Device.generateId())

  private val mockBootstrapCode = DeviceBootstrapCode(
    value = "test-code",
    owner = User.generateId(),
    device = Device.generateId(),
    expiresAt = Instant.now().plusSeconds(42)
  )
}
