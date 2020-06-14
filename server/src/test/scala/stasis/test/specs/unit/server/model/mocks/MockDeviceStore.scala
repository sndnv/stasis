package stasis.test.specs.unit.server.model.mocks

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.util.Timeout
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.server.model.devices.DeviceStore
import stasis.shared.model.devices.Device

object MockDeviceStore {
  def apply()(implicit system: ActorSystem[SpawnProtocol.Command], timeout: Timeout): DeviceStore = {
    val backend: MemoryBackend[Device.Id, Device] =
      MemoryBackend[Device.Id, Device](
        s"mock-device-store-${java.util.UUID.randomUUID()}"
      )

    DeviceStore(backend)(system.executionContext)
  }
}
