package stasis.server.service.actions

import scala.jdk.CollectionConverters._
import scala.util.Try

import io.github.sndnv.layers.service.actions.Action
import io.github.sndnv.layers.service.actions.ActionDefinition
import io.github.sndnv.layers.service.actions.ActionTrigger
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.server.service.CorePersistence
import stasis.server.service.ServerPersistence

trait ActionDefinitionProvider {
  def definitions: Seq[ActionDefinition]
}

object ActionDefinitionProvider {
  private val AvailableActions: Seq[ActionFactory[_ <: Action]] = Seq(
    CreateDatasetDefinitionAction.Factory
  )

  def apply(
    core: CorePersistence,
    server: ServerPersistence,
    config: com.typesafe.config.Config
  ): ActionDefinitionProvider = {
    implicit val log: Logger = LoggerFactory.getLogger(classOf[ActionDefinitionProvider].getName)
    new Default(
      core = core,
      server = server,
      actions = AvailableActions,
      config = config
    )
  }

  class Default(
    core: CorePersistence,
    server: ServerPersistence,
    actions: Seq[ActionFactory[_ <: Action]],
    config: com.typesafe.config.Config
  )(implicit log: Logger)
      extends ActionDefinitionProvider {
    private val actionFactories: Map[String, ActionFactory[_ <: Action]] =
      actions.map { factory => factory.actionName -> factory }.toMap

    override val definitions: Seq[ActionDefinition] =
      Try(config.getConfigList("actions").asScala.toSeq).getOrElse(Seq.empty).flatMap { actionConfig =>
        val actionName = actionConfig.getString("action")

        actionFactories.get(actionName) match {
          case Some(factory) =>
            val action = factory.create(core = core, server = server, config = actionConfig)

            action match {
              case action: Action.WithSchedule if actionConfig.hasPath("trigger.schedule") =>
                Some(
                  ActionDefinition.WithSchedule(
                    action = action,
                    trigger = ActionTrigger.Schedule(config = actionConfig.getConfig("trigger.schedule"))
                  )
                )

              case _: Action.WithSchedule =>
                log.error(
                  "Failed to create configured action [{}]; a schedule-based trigger is required but is not configured",
                  actionName
                )
                None

              case action: Action.WithEvent =>
                Some(
                  ActionDefinition.WithEvent(
                    action = action,
                    trigger = ActionTrigger.Event(name = action.trigger)
                  )
                )
            }

          case None =>
            log.error("Failed to create configured action [{}]; no such action exists", actionName)
            None
        }
      }
  }
}
