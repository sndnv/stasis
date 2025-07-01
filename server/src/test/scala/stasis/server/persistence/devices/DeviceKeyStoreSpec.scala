package stasis.server.persistence.devices

import java.time.Instant

import scala.util.control.NonFatal

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.util.ByteString

import stasis.core.routing.Node
import io.github.sndnv.layers.telemetry.TelemetryContext
import stasis.shared.model.devices.Device
import stasis.shared.model.devices.DeviceKey
import stasis.shared.model.users.User
import stasis.shared.security.Permission
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class DeviceKeyStoreSpec extends AsyncUnitSpec {
  "A DeviceKeyStore" should "provide a view resource (privileged)" in {
    val store = MockDeviceKeyStore()
    store.view().requiredPermission should be(Permission.View.Privileged)
  }

  it should "return existing device keys via view resource (privileged)" in {
    val store = MockDeviceKeyStore(withKeys = Seq(mockDeviceKey))

    store.view().get(mockDevice.id).map(result => result should be(Some(mockDeviceKey.copy(value = ByteString.empty))))
  }

  it should "return a list of device keys via view resource (privileged)" in {
    val store = MockDeviceKeyStore(
      withKeys = Seq(
        mockDeviceKey,
        mockDeviceKey.copy(device = Device.generateId()),
        mockDeviceKey.copy(device = Device.generateId())
      )
    )

    store.view().list().map { result =>
      result.size should be(3)
    }
  }

  it should "provide a view resource (self)" in {
    val store = MockDeviceKeyStore()
    store.viewSelf().requiredPermission should be(Permission.View.Self)
  }

  it should "check existence of device keys for own devices via view resource (self)" in {
    val store = MockDeviceKeyStore(withKeys = Seq(mockDeviceKey))

    store.viewSelf().exists(Seq(mockDevice.id), mockDevice.id).map(result => result should be(true))
  }

  it should "fail to check existence of existing device keys for other devices via view resource (self)" in {
    val store = MockDeviceKeyStore()

    store
      .viewSelf()
      .exists(Seq.empty, mockDevice.id)
      .map { response =>
        fail(s"Received unexpected response from store: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Expected to retrieve own device key but key for device [${mockDevice.id}] found"
        )
      }
  }

  it should "return existing device keys for own devices via view resource (self)" in {
    val store = MockDeviceKeyStore(withKeys = Seq(mockDeviceKey))

    store.viewSelf().get(Seq(mockDevice.id), mockDevice.id).map(result => result should be(Some(mockDeviceKey)))
  }

  it should "fail to return existing device keys for other devices via view resource (self)" in {
    val store = MockDeviceKeyStore()

    store
      .viewSelf()
      .get(Seq.empty, mockDevice.id)
      .map { response =>
        fail(s"Received unexpected response from store: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Expected to retrieve own device key but key for device [${mockDevice.id}] found"
        )
      }
  }

  it should "fail to return missing device keys for own devices via view resource (self)" in {
    val store = MockDeviceKeyStore()

    store.viewSelf().get(Seq(mockDevice.id), mockDevice.id).map(result => result should be(None))
  }

  it should "return a list of device keys for own devices via view resource (self)" in {
    val store = MockDeviceKeyStore(
      withKeys = Seq(
        mockDeviceKey,
        mockDeviceKey.copy(device = Device.generateId()),
        mockDeviceKey.copy(device = Device.generateId())
      )
    )

    store.viewSelf().list(Seq(mockDevice.id)).map { result =>
      result.size should be(1)
      result.headOption should be(Some(mockDeviceKey.copy(value = ByteString.empty)))
    }
  }

  it should "provide management resource (privileged)" in {
    val store = MockDeviceKeyStore()
    store.manage().requiredPermission should be(Permission.Manage.Privileged)
  }

  it should "allow deleting device keys via management resource (privileged)" in {
    val store = MockDeviceKeyStore(withKeys = Seq(mockDeviceKey))

    for {
      getResult <- store.view().get(mockDevice.id)
      deleteResult <- store.manage().delete(mockDevice.id)
      deletedGetResult <- store.view().get(mockDevice.id)
    } yield {
      getResult should be(Some(mockDeviceKey.copy(value = ByteString.empty)))
      deleteResult should be(true)
      deletedGetResult should be(None)
    }
  }

  it should "fail to delete missing devices via management resource (privileged)" in {
    val store = MockDeviceKeyStore()

    for {
      getResult <- store.view().get(mockDevice.id)
      deleteResult <- store.manage().delete(mockDevice.id)
    } yield {
      getResult should be(None)
      deleteResult should be(false)
    }
  }

  it should "provide management resource (self)" in {
    val store = MockDeviceKeyStore()
    store.manageSelf().requiredPermission should be(Permission.Manage.Self)
  }

  it should "allow creating device keys for own devices via management resource (self)" in {
    val store = MockDeviceKeyStore()

    for {
      createResult <- store.manageSelf().put(Seq(mockDevice.id), mockDeviceKey)
      getResult <- store.viewSelf().get(Seq(mockDevice.id), mockDevice.id)
    } yield {
      createResult should be(Done)
      getResult should be(Some(mockDeviceKey))
    }
  }

  it should "fail to create device keys for other devices via management resource (self)" in {
    val store = MockDeviceKeyStore()

    store
      .manageSelf()
      .put(Seq.empty, mockDeviceKey)
      .map { response =>
        fail(s"Received unexpected response from store: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Expected to put own device key but key for device [${mockDevice.id}] provided"
        )
      }
  }

  it should "allow deleting device keys for own devices via management resource (self)" in {
    val store = MockDeviceKeyStore(withKeys = Seq(mockDeviceKey))

    for {
      getResult <- store.viewSelf().get(Seq(mockDevice.id), mockDevice.id)
      deleteResult <- store.manageSelf().delete(Seq(mockDevice.id), mockDevice.id)
      deletedGetResult <- store.viewSelf().get(Seq(mockDevice.id), mockDevice.id)
    } yield {
      getResult should be(Some(mockDeviceKey))
      deleteResult should be(true)
      deletedGetResult should be(None)
    }
  }

  it should "fail to delete device keys for other devices via management resource (self)" in {
    val store = MockDeviceKeyStore(withKeys = Seq(mockDeviceKey))

    store
      .manageSelf()
      .delete(Seq.empty, mockDevice.id)
      .map { response =>
        fail(s"Received unexpected response from store: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Expected to delete own device key but key for device [${mockDevice.id}] provided"
        )
      }
  }

  it should "fail to delete missing device keys for own devices via management resource (self)" in {
    val store = MockDeviceKeyStore()

    for {
      getResult <- store.view().get(mockDevice.id)
      deleteResult <- store.manageSelf().delete(Seq(mockDevice.id), mockDevice.id)
    } yield {
      getResult should be(None)
      deleteResult should be(false)
    }
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "DeviceKeyStoreSpec"
  )

  private implicit val telemetry: TelemetryContext = MockTelemetryContext()

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

  private val mockDeviceKey = DeviceKey(
    value = ByteString("test-key"),
    owner = User.generateId(),
    device = mockDevice.id,
    created = Instant.now()
  )
}
