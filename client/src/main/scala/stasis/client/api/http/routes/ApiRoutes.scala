package stasis.client.api.http.routes

import akka.event.LoggingAdapter
import stasis.client.api.http.Context
import stasis.core.api.directives.EntityDiscardingDirectives

trait ApiRoutes extends EntityDiscardingDirectives {
  def log(implicit context: Context): LoggingAdapter = context.log
}
