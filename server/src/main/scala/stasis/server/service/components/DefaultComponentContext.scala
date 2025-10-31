package stasis.server.service.components

import io.github.sndnv.layers.telemetry.TelemetryContext
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

final case class DefaultComponentContext(
  system: ActorSystem[Nothing],
  timeout: Timeout,
  telemetry: TelemetryContext
) {
  val components: (ActorSystem[Nothing], Timeout, TelemetryContext) = (system, timeout, telemetry)
}

object DefaultComponentContext {
  implicit def componentsToContext(implicit
    system: ActorSystem[Nothing],
    timeout: Timeout,
    telemetry: TelemetryContext
  ): DefaultComponentContext = new DefaultComponentContext(
    system = system,
    timeout = timeout,
    telemetry = telemetry
  )
}
