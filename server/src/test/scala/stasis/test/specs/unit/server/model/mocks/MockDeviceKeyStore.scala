package stasis.test.specs.unit.server.model.mocks

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

import org.apache.pekko.actor.typed.{ActorSystem, SpawnProtocol}
import org.apache.pekko.util.Timeout
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.core.telemetry.TelemetryContext
import stasis.server.model.devices.DeviceKeyStore
import stasis.shared.model.devices.{Device, DeviceKey}

object MockDeviceKeyStore {
  def apply()(implicit
    system: ActorSystem[SpawnProtocol.Command],
    telemetry: TelemetryContext,
    timeout: Timeout
  ): DeviceKeyStore = MockDeviceKeyStore(withKeys = Seq.empty)

  def apply(withKeys: Seq[DeviceKey])(implicit
    system: ActorSystem[SpawnProtocol.Command],
    telemetry: TelemetryContext,
    timeout: Timeout
  ): DeviceKeyStore = {
    import system.executionContext

    val backend: MemoryBackend[Device.Id, DeviceKey] =
      MemoryBackend[Device.Id, DeviceKey](
        s"mock-device-key-store-${java.util.UUID.randomUUID()}"
      )

    val _ = Await.result(Future.sequence(withKeys.map(key => backend.put(key.device, key))), atMost = 1.second)

    DeviceKeyStore(backend)
  }
}
