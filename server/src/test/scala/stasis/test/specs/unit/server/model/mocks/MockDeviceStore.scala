package stasis.test.specs.unit.server.model.mocks

import org.apache.pekko.actor.typed.{ActorSystem, SpawnProtocol}
import org.apache.pekko.util.Timeout
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.core.telemetry.TelemetryContext
import stasis.server.model.devices.DeviceStore
import stasis.shared.model.devices.Device

object MockDeviceStore {
  def apply()(implicit
    system: ActorSystem[SpawnProtocol.Command],
    telemetry: TelemetryContext,
    timeout: Timeout
  ): DeviceStore = {
    val backend: MemoryBackend[Device.Id, Device] =
      MemoryBackend[Device.Id, Device](
        s"mock-device-store-${java.util.UUID.randomUUID()}"
      )

    DeviceStore(backend)(system.executionContext)
  }
}
