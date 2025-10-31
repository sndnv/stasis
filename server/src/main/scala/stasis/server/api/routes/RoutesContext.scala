package stasis.server.api.routes

import scala.concurrent.ExecutionContext

import io.github.sndnv.layers.events.EventCollector
import org.slf4j.Logger

import stasis.server.security.ResourceProvider

final case class RoutesContext(
  resourceProvider: ResourceProvider,
  eventCollector: EventCollector,
  ec: ExecutionContext,
  log: Logger
)

object RoutesContext {
  def collect()(implicit
    resourceProvider: ResourceProvider,
    eventCollector: EventCollector,
    ec: ExecutionContext,
    log: Logger
  ): RoutesContext =
    RoutesContext(
      resourceProvider = resourceProvider,
      eventCollector = eventCollector,
      ec = ec,
      log = log
    )
}
