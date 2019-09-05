package stasis.test.specs.unit.server.model.mocks

import akka.actor.ActorSystem
import akka.util.Timeout
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.server.model.devices.DeviceStore
import stasis.shared.model.devices.Device

object MockDeviceStore {
  def apply()(implicit system: ActorSystem, timeout: Timeout): DeviceStore = {
    val backend: MemoryBackend[Device.Id, Device] =
      MemoryBackend.untyped[Device.Id, Device](
        s"mock-device-store-${java.util.UUID.randomUUID()}"
      )

    DeviceStore(backend)(system.dispatcher)
  }
}
