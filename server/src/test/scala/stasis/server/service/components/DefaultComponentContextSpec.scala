package stasis.server.service.components

import scala.concurrent.duration._

import io.github.sndnv.layers.telemetry.TelemetryContext
import io.github.sndnv.layers.testing.UnitSpec
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.util.Timeout

import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class DefaultComponentContextSpec extends UnitSpec {
  "A DefaultComponentContext" should "provide its components" in {
    implicit val system: ActorSystem[Nothing] = ActorSystem(
      guardianBehavior = Behaviors.ignore,
      name = "DefaultComponentContextSpec"
    )
    implicit val timeout: Timeout = 3.seconds
    implicit val telemetry: TelemetryContext = MockTelemetryContext()

    val context: DefaultComponentContext = implicitly

    context.components should be((system, timeout, telemetry))
  }
}
