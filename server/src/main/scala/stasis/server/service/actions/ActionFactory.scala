package stasis.server.service.actions

import io.github.sndnv.layers.service.actions.Action

import stasis.server.service.CorePersistence
import stasis.server.service.ServerPersistence

trait ActionFactory[T <: Action] {
  def actionName: String

  def create(
    core: CorePersistence,
    server: ServerPersistence,
    config: com.typesafe.config.Config
  ): T
}
