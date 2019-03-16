package stasis.server.api.routes
import akka.event.LoggingAdapter
import stasis.server.security.{CurrentUser, ResourceProvider}

import scala.concurrent.ExecutionContext

final case class RoutesContext(
  resourceProvider: ResourceProvider,
  user: CurrentUser,
  ec: ExecutionContext,
  log: LoggingAdapter
)

object RoutesContext {
  def collect()(
    implicit
    resourceProvider: ResourceProvider,
    user: CurrentUser,
    ec: ExecutionContext,
    log: LoggingAdapter
  ): RoutesContext = RoutesContext(resourceProvider, user, ec, log)
}
