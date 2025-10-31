package stasis.server.service.components

import io.github.sndnv.layers.events.DefaultEventCollector
import io.github.sndnv.layers.events.EventCollector
import io.github.sndnv.layers.service.components.auto._
import io.github.sndnv.layers.telemetry.TelemetryContext
import io.github.sndnv.layers.testing.UnitSpec
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class EventsComponentLoaderSpec extends UnitSpec {
  "An EventsComponentLoader" should "provide its name and component name" in {
    EventsComponentLoader.name should be("events")
    EventsComponentLoader.component should be(Some("collector"))
  }

  it should "create its component and render its config (noop)" in {
    val component = EventsComponentLoader.create(config = config.getConfig("noop"))

    component.component should be(an[EventCollector.NoOp.type])
    component.renderConfig(withPrefix = "") should be("none")
  }

  it should "create its component and render its config (default)" in {
    val component = EventsComponentLoader.create(config = config.getConfig("default"))

    component.component should be(an[DefaultEventCollector])

    val rendered = component.renderConfig(withPrefix = "")

    rendered should include("subscriber-buffer-size: 100")
    rendered should include("quiet-period:           3 seconds")
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "EventsComponentLoaderSpec"
  )

  private implicit val telemetry: TelemetryContext = MockTelemetryContext()

  private val config = typedSystem.settings.config.getConfig("stasis.test.server.service.components.events")
}
