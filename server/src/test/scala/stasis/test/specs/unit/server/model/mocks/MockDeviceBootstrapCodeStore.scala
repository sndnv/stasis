package stasis.test.specs.unit.server.model.mocks

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import stasis.layers.persistence.memory.MemoryStore
import stasis.layers.telemetry.TelemetryContext
import stasis.server.model.devices.DeviceBootstrapCodeStore
import stasis.shared.model.devices.DeviceBootstrapCode

object MockDeviceBootstrapCodeStore {
  def apply()(implicit
    system: ActorSystem[Nothing],
    telemetry: TelemetryContext,
    timeout: Timeout
  ): DeviceBootstrapCodeStore = {
    val backend: MemoryStore[String, DeviceBootstrapCode] =
      MemoryStore[String, DeviceBootstrapCode](
        name = s"mock-device-bootstrap-code-store-${java.util.UUID.randomUUID()}"
      )

    DeviceBootstrapCodeStore(backend)
  }
}
