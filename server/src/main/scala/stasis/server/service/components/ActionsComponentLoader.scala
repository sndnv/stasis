package stasis.server.service.components

import java.io.File

import scala.concurrent.duration._

import com.typesafe.config.Config
import io.github.sndnv.layers.events.EventCollector
import io.github.sndnv.layers.service.actions.ActionExecutor
import io.github.sndnv.layers.service.actions.DefaultActionExecutor
import io.github.sndnv.layers.service.components.Component
import io.github.sndnv.layers.service.components.ComponentLoader
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import stasis.server.service.CorePersistence
import stasis.server.service.ServerPersistence
import stasis.server.service.actions.ActionDefinitionProvider

object ActionsComponentLoader
    extends ComponentLoader.Optional[
      ActionExecutor,
      (DefaultComponentContext, EventCollector, ActionDefinitionProvider)
    ] {
  override val name: String = "actions"
  override val component: Option[String] = Some("executor")

  override protected def noop(): Component[ActionExecutor] =
    Component.withoutConfig(ActionExecutor.NoOp)

  override protected def default(
    config: Config
  )(implicit
    context: ComponentLoader.Context[
      (DefaultComponentContext, EventCollector, ActionDefinitionProvider)
    ]
  ): Component[ActionExecutor] = {
    val (base, events, definitionProvider) = context.value
    implicit val (system, timeout, _) = base.components

    val executorConfig = DefaultActionExecutor.Config(
      definitions = definitionProvider.definitions,
      throttling = DefaultActionExecutor.Config.Throttling(
        actions = config.getInt("default.throttling.actions"),
        per = config.getDuration("default.throttling.per").toMillis.millis
      ),
      history = DefaultActionExecutor.Config.History(
        maxSize = config.getLong("default.history.max-size")
      )
    )

    new DefaultActionExecutorComponent(
      config = executorConfig,
      events = events
    )
  }

  def createActionDefinitionProvider(
    config: Config,
    corePersistence: CorePersistence,
    serverPersistence: ServerPersistence
  ): ActionDefinitionProvider =
    ActionDefinitionProvider(
      core = corePersistence,
      server = serverPersistence,
      config = loadActionsFile(configFile = config.getString("config"))
    )

  private class DefaultActionExecutorComponent(
    config: DefaultActionExecutor.Config,
    events: EventCollector
  )(implicit system: ActorSystem[Nothing], timeout: Timeout)
      extends Component[DefaultActionExecutor] {
    override def renderConfig(withPrefix: String): String =
      s"""
         |$withPrefix  definitions [${config.definitions.length.toString}]:
         |$withPrefix    ${config.definitions
          .map(d => s"[${d.action.name}] triggered by [${d.trigger.description}]")
          .mkString(s"\n$withPrefix    ")}
         |$withPrefix  throttling:
         |$withPrefix    actions: ${config.throttling.actions.toString}
         |$withPrefix    per:     ${config.throttling.per.toCoarsest.toString}
         |$withPrefix  history:
         |$withPrefix    max-size: ${config.history.maxSize.toString}""".stripMargin

    override val component: DefaultActionExecutor =
      DefaultActionExecutor(
        config = config,
        events = events
      )
  }

  private def loadActionsFile(configFile: String): com.typesafe.config.Config =
    com.typesafe.config.ConfigFactory
      .parseFile(
        Option(getClass.getClassLoader.getResource(configFile))
          .map(resource => new File(resource.getFile))
          .getOrElse(new File(configFile))
      )
      .resolve()
}
