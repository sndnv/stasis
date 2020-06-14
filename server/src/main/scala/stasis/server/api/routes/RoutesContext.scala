package stasis.server.api.routes

import akka.stream.Materializer
import org.slf4j.Logger
import stasis.server.security.ResourceProvider

import scala.concurrent.ExecutionContext

final case class RoutesContext(
  resourceProvider: ResourceProvider,
  ec: ExecutionContext,
  mat: Materializer,
  log: Logger
)

object RoutesContext {
  def collect()(
    implicit
    resourceProvider: ResourceProvider,
    ec: ExecutionContext,
    mat: Materializer,
    log: Logger
  ): RoutesContext =
    RoutesContext(
      resourceProvider = resourceProvider,
      ec = ec,
      mat = mat,
      log = log
    )
}
