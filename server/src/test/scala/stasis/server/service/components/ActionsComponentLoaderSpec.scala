package stasis.server.service.components

import io.github.sndnv.layers.events.EventCollector
import io.github.sndnv.layers.service.actions.ActionDefinition
import io.github.sndnv.layers.service.actions.ActionExecutor
import io.github.sndnv.layers.service.actions.DefaultActionExecutor
import io.github.sndnv.layers.service.components.auto._
import io.github.sndnv.layers.telemetry.TelemetryContext
import io.github.sndnv.layers.testing.UnitSpec
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.mockito.scalatest.AsyncMockitoSugar

import stasis.server.persistence.datasets.MockDatasetDefinitionStore
import stasis.server.service.CorePersistence
import stasis.server.service.ServerPersistence
import stasis.server.service.actions.ActionDefinitionProvider
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class ActionsComponentLoaderSpec extends UnitSpec with AsyncMockitoSugar {
  "An ActionsComponentLoader" should "provide its name and component name" in {
    ActionsComponentLoader.name should be("actions")
    ActionsComponentLoader.component should be(Some("executor"))
  }

  it should "create its component and render its config (noop)" in {
    implicit val events: EventCollector = EventCollector.NoOp

    implicit val provider: ActionDefinitionProvider = new ActionDefinitionProvider {
      override def definitions: Seq[ActionDefinition] = Seq.empty
    }

    val component = ActionsComponentLoader.create(config = config.getConfig("noop"))

    component.component should be(an[ActionExecutor.NoOp.type])
    component.renderConfig(withPrefix = "") should be("none")
  }

  it should "create its component and render its config (default)" in {
    implicit val events: EventCollector = EventCollector.NoOp

    val server = mock[ServerPersistence]

    when(server.datasetDefinitions).thenAnswer(MockDatasetDefinitionStore())

    implicit val provider: ActionDefinitionProvider = ActionDefinitionProvider(
      core = mock[CorePersistence],
      server = server,
      config = com.typesafe.config.ConfigFactory.load("example-actions")
    )

    val component = ActionsComponentLoader.create(config = config.getConfig("default"))

    component.component should be(an[DefaultActionExecutor])

    val rendered = component.renderConfig(withPrefix = "")

    rendered should include("definitions [1]:")
    rendered should include("[CreateDatasetDefinitionAction] triggered by [event-name=device_created]")
    rendered should include("throttling:")
    rendered should include("actions: 100")
    rendered should include("per:     1 second")
    rendered should include("history:")
    rendered should include("max-size: 1000")
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "ActionsComponentLoaderSpec"
  )

  private implicit val telemetry: TelemetryContext = MockTelemetryContext()

  private val config = typedSystem.settings.config.getConfig("stasis.test.server.service.components.actions")
}
