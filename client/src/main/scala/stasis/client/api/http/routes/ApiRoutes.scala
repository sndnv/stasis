package stasis.client.api.http.routes

import org.slf4j.Logger
import stasis.client.api.http.Context
import stasis.core.api.directives.EntityDiscardingDirectives

trait ApiRoutes extends EntityDiscardingDirectives {
  def log(implicit context: Context): Logger = context.log
}
