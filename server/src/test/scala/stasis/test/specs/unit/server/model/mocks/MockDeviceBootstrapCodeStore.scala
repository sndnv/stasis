package stasis.test.specs.unit.server.model.mocks

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.util.Timeout
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.core.telemetry.TelemetryContext
import stasis.server.model.devices.DeviceBootstrapCodeStore
import stasis.shared.model.devices.DeviceBootstrapCode

object MockDeviceBootstrapCodeStore {
  def apply()(implicit
    system: ActorSystem[SpawnProtocol.Command],
    telemetry: TelemetryContext,
    timeout: Timeout
  ): DeviceBootstrapCodeStore = {
    val backend: MemoryBackend[String, DeviceBootstrapCode] =
      MemoryBackend[String, DeviceBootstrapCode](
        name = s"mock-device-bootstrap-code-store-${java.util.UUID.randomUUID()}"
      )

    DeviceBootstrapCodeStore(backend)
  }
}
