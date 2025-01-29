package stasis.server.persistence.devices

import java.time.Instant

import scala.util.control.NonFatal

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.core.commands.proto.Command
import stasis.core.commands.proto.CommandParameters
import stasis.core.commands.proto.CommandSource
import stasis.layers.telemetry.TelemetryContext
import stasis.shared.model.devices.Device
import stasis.shared.security.Permission
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class DeviceCommandStoreSpec extends AsyncUnitSpec {
  "A DeviceCommandStore" should "provide a store name" in {
    val store = MockDeviceCommandStore()

    store.name() should startWith("mock-device-command-message-store")
  }

  it should "provide store migrations" in {
    val store = MockDeviceCommandStore()

    store.migrations().size should be(0) // no migrations expected for mock store
  }

  it should "provide store init and drop" in {
    val store = MockDeviceCommandStore()

    noException should be thrownBy store.init().await
    noException should be thrownBy store.drop().await
  }

  it should "provide a view resource (service)" in {
    val store = MockDeviceCommandStore()
    store.view().requiredPermission should be(Permission.View.Service)
  }

  it should "return a list of all device commands via view resource (service)" in {
    val store = MockDeviceCommandStore(
      withMessages = Seq(
        mockCommand,
        mockCommand.copy(target = Some(Device.generateId())),
        mockCommand.copy(target = Some(Device.generateId()))
      )
    )

    store.view().list().map { result =>
      result.size should be(3)
    }
  }

  it should "return a list of device commands for a specific device via view resource (service)" in {
    val store = MockDeviceCommandStore(
      withMessages = Seq(
        mockCommand,
        mockCommand,
        mockCommand.copy(target = Some(Device.generateId()))
      )
    )

    store.view().list(forDevice = mockDeviceId).map { result =>
      result.size should be(2)
    }
  }

  it should "provide a view resource (self)" in {
    val store = MockDeviceCommandStore()
    store.viewSelf().requiredPermission should be(Permission.View.Self)
  }

  it should "return a list of device commands for own device via view resource (self)" in {
    val store = MockDeviceCommandStore(
      withMessages = Seq(
        mockCommand,
        mockCommand,
        mockCommand.copy(target = Some(Device.generateId()))
      )
    )

    store.viewSelf().list(ownDevices = Seq(mockDeviceId), forDevice = mockDeviceId).map { result =>
      result.size should be(2)
    }
  }

  it should "fail to return a list of device commands for other devices via view resource (self)" in {
    val store = MockDeviceCommandStore()

    store
      .viewSelf()
      .list(ownDevices = Seq.empty, forDevice = mockDeviceId)
      .map { response =>
        fail(s"Received unexpected response from store: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Expected to retrieve commands for own device but commands for device [$mockDeviceId] requested"
        )
      }
  }

  it should "return a list of unprocessed device commands for own device via view resource (self)" in {
    val store = MockDeviceCommandStore(
      withMessages = Seq(
        mockCommand,
        mockCommand,
        mockCommand.copy(target = Some(Device.generateId()))
      )
    )

    store.viewSelf().list(ownDevices = Seq(mockDeviceId), forDevice = mockDeviceId, lastSequenceId = 1).map { result =>
      result.size should be(1)
    }
  }

  it should "fail to return a list of unprocessed device commands for other devices via view resource (self)" in {
    val store = MockDeviceCommandStore()

    store
      .viewSelf()
      .list(ownDevices = Seq.empty, forDevice = mockDeviceId, lastSequenceId = 1)
      .map { response =>
        fail(s"Received unexpected response from store: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Expected to retrieve commands for own device but commands for device [$mockDeviceId] requested"
        )
      }
  }

  it should "provide management resource (service)" in {
    val store = MockDeviceCommandStore()
    store.manage().requiredPermission should be(Permission.Manage.Service)
  }

  it should "allow creating device commands via management resource (service)" in {
    val store = MockDeviceCommandStore()

    for {
      createResult <- store.manage().put(mockCommand)
      listResult <- store.view().list()
    } yield {
      createResult should be(Done)
      listResult should be(Seq(mockCommand.copy(sequenceId = 1)))
    }
  }

  it should "allow deleting device commands via management resource (service)" in {
    val store = MockDeviceCommandStore(
      withMessages = Seq(
        mockCommand,
        mockCommand,
        mockCommand.copy(target = Some(Device.generateId()))
      )
    )

    for {
      deleteResult <- store.manage().delete(sequenceId = 2)
      listResult <- store.view().list()
    } yield {
      deleteResult should be(true)
      listResult.size should be(2)
    }
  }

  it should "allow truncating old device commands via management resource (service)" in {
    val store = MockDeviceCommandStore(
      withMessages = Seq(
        mockCommand.copy(created = Instant.now().minusSeconds(30)),
        mockCommand.copy(created = Instant.now().minusSeconds(10)),
        mockCommand.copy(created = Instant.now().plusSeconds(30))
      )
    )

    for {
      listResultBefore <- store.view().list()
      _ <- store.manage().truncate(olderThan = Instant.now())
      listResultAfter <- store.view().list()
    } yield {
      listResultBefore.size should be(3)
      listResultAfter.size should be(1)
    }
  }

  it should "provide management resource (self)" in {
    val store = MockDeviceCommandStore()
    store.manageSelf().requiredPermission should be(Permission.Manage.Self)
  }

  it should "allow creating device commands for own device via management resource (self)" in {
    val store = MockDeviceCommandStore()

    for {
      createResult <- store.manageSelf().put(ownDevices = Seq(mockDeviceId), command = mockCommand)
      listResult <- store.viewSelf().list(ownDevices = Seq(mockDeviceId), forDevice = mockDeviceId)
    } yield {
      createResult should be(Done)
      listResult should be(Seq(mockCommand.copy(sequenceId = 1)))
    }
  }

  it should "fail to create device commands for other devices via management resource (self)" in {
    val store = MockDeviceCommandStore()

    store
      .manageSelf()
      .put(ownDevices = Seq.empty, command = mockCommand)
      .map { response =>
        fail(s"Received unexpected response from store: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Expected to put command for own device but command for device [$mockDeviceId] provided"
        )
      }
  }

  it should "fail to create device commands without a target device via management resource (self)" in {
    val store = MockDeviceCommandStore()

    store
      .manageSelf()
      .put(ownDevices = Seq(mockDeviceId), command = mockCommand.copy(target = None))
      .map { response =>
        fail(s"Received unexpected response from store: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          "Expected to put command for own device but command without a target provided"
        )
      }
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "DeviceCommandStoreSpec"
  )

  private implicit val telemetry: TelemetryContext = MockTelemetryContext()

  private val mockDeviceId = Device.generateId()

  private val mockCommand = Command(
    sequenceId = 0,
    source = CommandSource.User,
    target = Some(mockDeviceId),
    parameters = CommandParameters.Empty,
    created = Instant.now()
  )
}
