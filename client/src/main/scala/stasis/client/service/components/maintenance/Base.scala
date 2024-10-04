package stasis.client.service.components.maintenance

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

import com.typesafe.{config => typesafe}
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout
import org.slf4j.Logger

import stasis.client.ops
import stasis.client.service.ApplicationArguments
import stasis.client.service.ApplicationArguments.Mode
import stasis.client.service.ApplicationDirectory
import stasis.client.service.components.internal.ConfigOverride
import stasis.client.service.components.internal.FutureOps
import stasis.core
import stasis.layers
import stasis.layers.telemetry.DefaultTelemetryContext
import stasis.layers.telemetry.TelemetryContext

trait Base extends FutureOps {
  implicit def system: ActorSystem[Nothing]
  implicit def ec: ExecutionContext
  implicit def log: Logger
  implicit def telemetry: TelemetryContext

  def args: ApplicationArguments.Mode.Maintenance

  def directory: ApplicationDirectory

  def configOverride: typesafe.Config
  def rawConfig: typesafe.Config

  implicit def timeout: Timeout
}

object Base {
  def apply(
    modeArguments: ApplicationArguments.Mode.Maintenance,
    applicationDirectory: ApplicationDirectory
  )(implicit
    typedSystem: ActorSystem[Nothing],
    logger: Logger
  ): Future[Base] =
    Future.fromTry(
      Try {
        new Base {
          override implicit val system: ActorSystem[Nothing] = typedSystem
          override implicit val ec: ExecutionContext = typedSystem.executionContext
          override implicit val log: Logger = logger

          override val args: Mode.Maintenance = modeArguments
          override val directory: ApplicationDirectory = applicationDirectory

          override val configOverride: typesafe.Config =
            ConfigOverride.load(directory)

          override val rawConfig: typesafe.Config =
            configOverride.withFallback(system.settings.config).getConfig("stasis.client").resolve()

          override implicit val telemetry: TelemetryContext = DefaultTelemetryContext(
            metricsProviders = Set(
              layers.security.Metrics.noop(),
              layers.api.Metrics.noop(),
              layers.persistence.Metrics.noop(),
              core.persistence.Metrics.noop(),
              ops.Metrics.noop()
            ).flatten
          )

          override implicit val timeout: Timeout =
            rawConfig.getDuration("service.internal-query-timeout").toMillis.millis
        }
      }
    )
}
