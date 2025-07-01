package stasis.client.api.http.routes

import org.slf4j.Logger

import stasis.client.api.Context
import io.github.sndnv.layers.api.directives.EntityDiscardingDirectives

trait ApiRoutes extends EntityDiscardingDirectives {
  def log(implicit context: Context): Logger = context.log
}
