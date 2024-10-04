package stasis.test.specs.unit.server.model.mocks

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import stasis.layers.persistence.memory.MemoryStore
import stasis.layers.telemetry.TelemetryContext
import stasis.server.model.devices.DeviceStore
import stasis.shared.model.devices.Device

object MockDeviceStore {
  def apply()(implicit
    system: ActorSystem[Nothing],
    telemetry: TelemetryContext,
    timeout: Timeout
  ): DeviceStore = {
    val backend: MemoryStore[Device.Id, Device] =
      MemoryStore[Device.Id, Device](
        s"mock-device-store-${java.util.UUID.randomUUID()}"
      )

    DeviceStore(backend)(system.executionContext)
  }
}
