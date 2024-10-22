package stasis.server.persistence.devices

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.layers.UnitSpec
import stasis.layers.persistence.SlickTestDatabase
import stasis.layers.persistence.memory.MemoryStore
import stasis.layers.telemetry.MockTelemetryContext
import stasis.shared.model.devices.DeviceBootstrapCode
import stasis.test.specs.unit.shared.model.Generators

class DefaultDeviceBootstrapCodeStoreSpec extends UnitSpec with SlickTestDatabase {
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

  it should "find and consume device bootstrap codes" in withRetry {
    val underlying = MemoryStore[String, DeviceBootstrapCode](
      name = s"mock-device-bootstrap-code-store-${java.util.UUID.randomUUID()}"
    )
    val store = new DefaultDeviceBootstrapCodeStore(name = "TEST_DEVICE_CODES", backend = underlying)
    val expectedDeviceCode = Generators.generateDeviceBootstrapCode

    for {
      _ <- store.init()
      _ <- store.put(expectedDeviceCode)
      actualCode <- store.find(expectedDeviceCode.device)
      someCodes <- store.list()
      _ <- store.consume(expectedDeviceCode.value)
      missingCode <- store.find(expectedDeviceCode.device)
      noCodes <- store.list()
      _ <- store.drop()
    } yield {
      actualCode should be(Some(expectedDeviceCode))
      someCodes should be(Seq(expectedDeviceCode.copy(value = "*****")))
      missingCode should be(None)
      noCodes should be(Seq.empty)
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
