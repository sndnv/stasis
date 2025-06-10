package stasis.client.service.components.bootstrap

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout
import org.slf4j.Logger

import stasis.client.ops
import stasis.client.service.ApplicationArguments
import stasis.client.service.ApplicationArguments.Mode
import stasis.client.service.ApplicationDirectory
import stasis.client.service.ApplicationTemplates
import stasis.client.service.components.internal.FutureOps
import stasis.core
import stasis.layers
import stasis.layers.telemetry.DefaultTelemetryContext
import stasis.layers.telemetry.TelemetryContext
import stasis.layers.telemetry.analytics.AnalyticsCollector

trait Base extends FutureOps {
  implicit def system: ActorSystem[Nothing]
  implicit def ec: ExecutionContext
  implicit def log: Logger
  implicit def telemetry: TelemetryContext

  def args: ApplicationArguments.Mode.Bootstrap

  def directory: ApplicationDirectory
  def templates: ApplicationTemplates

  implicit def timeout: Timeout
}

object Base {
  def apply(
    modeArguments: ApplicationArguments.Mode.Bootstrap,
    applicationDirectory: ApplicationDirectory
  )(implicit
    typedSystem: ActorSystem[Nothing],
    logger: Logger
  ): Future[Base] =
    Future.fromTry(
      for {
        applicationTemplates <- ApplicationTemplates()
      } yield {
        new Base {
          override implicit val system: ActorSystem[Nothing] = typedSystem
          override implicit val ec: ExecutionContext = typedSystem.executionContext
          override implicit val log: Logger = logger

          override implicit val telemetry: TelemetryContext = DefaultTelemetryContext(
            metricsProviders = Set(
              layers.security.Metrics.noop(),
              layers.api.Metrics.noop(),
              layers.persistence.Metrics.noop(),
              core.persistence.Metrics.noop(),
              ops.Metrics.noop()
            ).flatten,
            analyticsCollector = AnalyticsCollector.NoOp
          )

          override val args: Mode.Bootstrap = modeArguments
          override val directory: ApplicationDirectory = applicationDirectory
          override val templates: ApplicationTemplates = applicationTemplates

          override implicit val timeout: Timeout = 5.seconds
        }
      }
    )
}
