package stasis.test.specs.unit.server.model.mocks

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import stasis.layers.persistence.memory.MemoryStore
import stasis.layers.telemetry.TelemetryContext
import stasis.server.model.devices.DeviceKeyStore
import stasis.shared.model.devices.Device
import stasis.shared.model.devices.DeviceKey

object MockDeviceKeyStore {
  def apply()(implicit
    system: ActorSystem[Nothing],
    telemetry: TelemetryContext,
    timeout: Timeout
  ): DeviceKeyStore = MockDeviceKeyStore(withKeys = Seq.empty)

  def apply(withKeys: Seq[DeviceKey])(implicit
    system: ActorSystem[Nothing],
    telemetry: TelemetryContext,
    timeout: Timeout
  ): DeviceKeyStore = {
    import system.executionContext

    val backend: MemoryStore[Device.Id, DeviceKey] =
      MemoryStore[Device.Id, DeviceKey](
        s"mock-device-key-store-${java.util.UUID.randomUUID()}"
      )

    val _ = Await.result(Future.sequence(withKeys.map(key => backend.put(key.device, key))), atMost = 1.second)

    DeviceKeyStore(backend)
  }
}
