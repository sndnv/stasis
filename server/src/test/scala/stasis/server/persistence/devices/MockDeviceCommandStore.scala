package stasis.server.persistence.devices

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import stasis.core.commands.proto.Command
import io.github.sndnv.layers.telemetry.TelemetryContext

object MockDeviceCommandStore {
  def apply()(implicit
    system: ActorSystem[Nothing],
    telemetry: TelemetryContext,
    timeout: Timeout
  ): DeviceCommandStore =
    DeviceCommandStore(store = MockCommandStore())

  def apply(withMessages: Seq[Command])(implicit
    system: ActorSystem[Nothing],
    telemetry: TelemetryContext,
    timeout: Timeout
  ): DeviceCommandStore =
    DeviceCommandStore(store = MockCommandStore(withMessages))
}
