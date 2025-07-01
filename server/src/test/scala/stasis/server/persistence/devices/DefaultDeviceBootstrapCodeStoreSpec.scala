package stasis.server.persistence.devices

import java.time.Instant

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import io.github.sndnv.layers.testing.UnitSpec
import io.github.sndnv.layers.testing.persistence.TestSlickDatabase
import io.github.sndnv.layers.persistence.memory.MemoryStore
import io.github.sndnv.layers.telemetry.mocks.MockTelemetryContext
import stasis.shared.api.requests.CreateDeviceOwn
import stasis.shared.model.devices.Device
import stasis.shared.model.devices.DeviceBootstrapCode
import stasis.shared.model.users.User
import stasis.test.specs.unit.shared.model.Generators

class DefaultDeviceBootstrapCodeStoreSpec extends UnitSpec with TestSlickDatabase {
  "A DefaultDeviceBootstrapCodeStore" should "add, retrieve and delete device bootstrap codes" in withRetry {
    val underlying = MemoryStore[String, DeviceBootstrapCode](
      name = s"mock-device-bootstrap-code-store-${java.util.UUID.randomUUID()}"
    )
    val store = new DefaultDeviceBootstrapCodeStore(name = "TEST_DEVICE_CODES", backend = underlying)
    val expectedDeviceCode = Generators.generateDeviceBootstrapCode

    for {
      _ <- store.init()
      _ <- store.put(expectedDeviceCode)
      actualCode <- store.get(expectedDeviceCode.value)
      someCodes <- store.list()
      _ <- store.delete(expectedDeviceCode.value)
      missingCode <- store.get(expectedDeviceCode.value)
      noCodes <- store.list()
      _ <- store.drop()
    } yield {
      actualCode should be(Some(expectedDeviceCode))
      someCodes should be(Seq(expectedDeviceCode.copy(value = "*****")))
      missingCode should be(None)
      noCodes should be(Seq.empty)
    }
  }

  it should "consume device bootstrap codes" in withRetry {
    val underlying = MemoryStore[String, DeviceBootstrapCode](
      name = s"mock-device-bootstrap-code-store-${java.util.UUID.randomUUID()}"
    )
    val store = new DefaultDeviceBootstrapCodeStore(name = "TEST_DEVICE_CODES", backend = underlying)
    val expectedDeviceCode = Generators.generateDeviceBootstrapCode

    for {
      _ <- store.init()
      _ <- store.put(expectedDeviceCode)
      someCodes <- store.list()
      _ <- store.consume(expectedDeviceCode.value)
      noCodes <- store.list()
      _ <- store.drop()
    } yield {
      someCodes should be(Seq(expectedDeviceCode.copy(value = "*****")))
      noCodes should be(Seq.empty)
    }
  }

  it should "retrieve and delete device bootstrap codes based on IDs" in withRetry {
    val underlying = MemoryStore[String, DeviceBootstrapCode](
      name = s"mock-device-bootstrap-code-store-${java.util.UUID.randomUUID()}"
    )
    val store = new DefaultDeviceBootstrapCodeStore(name = "TEST_DEVICE_CODES", backend = underlying)
    val expectedDeviceCode = Generators.generateDeviceBootstrapCode

    for {
      _ <- store.init()
      _ <- store.put(expectedDeviceCode)
      someCodes <- store.list()
      existingCode <- store.get(expectedDeviceCode.id)
      deleteResult <- store.delete(expectedDeviceCode.id)
      noCodes <- store.list()
      _ <- store.drop()
    } yield {
      someCodes should be(Seq(expectedDeviceCode.copy(value = "*****")))
      existingCode should be(Some(expectedDeviceCode))
      deleteResult should be(true)
      noCodes should be(Seq.empty)
    }
  }

  it should "not delete missing device bootstrap codes" in withRetry {
    val underlying = MemoryStore[String, DeviceBootstrapCode](
      name = s"mock-device-bootstrap-code-store-${java.util.UUID.randomUUID()}"
    )
    val store = new DefaultDeviceBootstrapCodeStore(name = "TEST_DEVICE_CODES", backend = underlying)

    val codeId = DeviceBootstrapCode.generateId()

    for {
      _ <- store.init()
      codesBefore <- store.list()
      missingCode <- store.get(codeId)
      deleteResult <- store.delete(codeId)
      codesAfter <- store.list()
      _ <- store.drop()
    } yield {
      codesBefore should be(Seq.empty)
      missingCode should be(None)
      deleteResult should be(false)
      codesAfter should be(Seq.empty)
    }
  }

  it should "replace old device bootstrap codes for existing devices" in withRetry {
    val underlying = MemoryStore[String, DeviceBootstrapCode](
      name = s"mock-device-bootstrap-code-store-${java.util.UUID.randomUUID()}"
    )
    val store = new DefaultDeviceBootstrapCodeStore(name = "TEST_DEVICE_CODES", backend = underlying)

    val oldDeviceCode = DeviceBootstrapCode(
      value = "old-code",
      owner = User.generateId(),
      device = Device.generateId(),
      expiresAt = Instant.now().plusSeconds(42L)
    )

    val newDeviceCode = oldDeviceCode.copy(
      value = "new-code"
    )

    for {
      _ <- store.init()
      _ <- store.put(oldDeviceCode)
      initialCodes <- store.list()
      _ <- store.put(newDeviceCode)
      updatedCodes <- store.list()
      _ <- store.drop()
    } yield {
      initialCodes should be(Seq(oldDeviceCode.copy(value = "*****")))
      updatedCodes should be(Seq(newDeviceCode.copy(value = "*****")))
    }
  }

  it should "replace old device bootstrap codes for new devices" in withRetry {
    val underlying = MemoryStore[String, DeviceBootstrapCode](
      name = s"mock-device-bootstrap-code-store-${java.util.UUID.randomUUID()}"
    )
    val store = new DefaultDeviceBootstrapCodeStore(name = "TEST_DEVICE_CODES", backend = underlying)

    val oldDeviceCode = DeviceBootstrapCode(
      value = "old-code",
      owner = User.generateId(),
      request = CreateDeviceOwn(name = "test-name", limits = None),
      expiresAt = Instant.now().plusSeconds(42L)
    )

    val newDeviceCode = oldDeviceCode.copy(
      value = "new-code"
    )

    for {
      _ <- store.init()
      _ <- store.put(oldDeviceCode)
      initialCodes <- store.list()
      _ <- store.put(newDeviceCode)
      updatedCodes <- store.list()
      _ <- store.drop()
    } yield {
      initialCodes should be(Seq(oldDeviceCode.copy(value = "*****")))
      updatedCodes should be(Seq(newDeviceCode.copy(value = "*****")))
    }
  }

  it should "provide no migrations" in withRetry {
    val underlying = MemoryStore[String, DeviceBootstrapCode](
      name = s"mock-device-bootstrap-code-store-${java.util.UUID.randomUUID()}"
    )
    val store = new DefaultDeviceBootstrapCodeStore(name = "TEST_DEVICE_CODES", backend = underlying)
    store.migrations should be(empty)
  }

  private implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    "DefaultDeviceBootstrapCodeStoreSpec"
  )
}
