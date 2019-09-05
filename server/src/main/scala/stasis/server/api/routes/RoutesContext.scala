package stasis.server.api.routes

import akka.event.LoggingAdapter
import akka.stream.Materializer
import stasis.server.security.{CurrentUser, ResourceProvider}

import scala.concurrent.ExecutionContext

final case class RoutesContext(
  resourceProvider: ResourceProvider,
  ec: ExecutionContext,
  mat: Materializer,
  log: LoggingAdapter
)

object RoutesContext {
  def collect()(
    implicit
    resourceProvider: ResourceProvider,
    ec: ExecutionContext,
    mat: Materializer,
    log: LoggingAdapter
  ): RoutesContext =
    RoutesContext(
      resourceProvider = resourceProvider,
      ec = ec,
      mat = mat,
      log = log
    )
}
