package stasis.server.api.routes

import org.slf4j.Logger
import stasis.server.security.ResourceProvider

import scala.concurrent.ExecutionContext

final case class RoutesContext(
  resourceProvider: ResourceProvider,
  ec: ExecutionContext,
  log: Logger
)

object RoutesContext {
  def collect()(implicit
    resourceProvider: ResourceProvider,
    ec: ExecutionContext,
    log: Logger
  ): RoutesContext =
    RoutesContext(
      resourceProvider = resourceProvider,
      ec = ec,
      log = log
    )
}
