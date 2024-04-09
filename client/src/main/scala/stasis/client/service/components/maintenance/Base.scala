package stasis.client.service.components.maintenance

import com.typesafe.{config => typesafe}
import org.apache.pekko.actor.typed.{ActorSystem, SpawnProtocol}
import org.apache.pekko.util.Timeout
import org.slf4j.Logger
import stasis.client.service.ApplicationArguments.Mode
import stasis.client.service.components.internal.{ConfigOverride, FutureOps}
import stasis.client.service.{ApplicationArguments, ApplicationDirectory}
import stasis.core.telemetry.{DefaultTelemetryContext, TelemetryContext}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

import stasis.client.ops
import stasis.core.{api, persistence, security}

trait Base extends FutureOps {
  implicit def system: ActorSystem[SpawnProtocol.Command]
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
    typedSystem: ActorSystem[SpawnProtocol.Command],
    logger: Logger
  ): Future[Base] =
    Future.fromTry(
      Try {
        new Base {
          override implicit val system: ActorSystem[SpawnProtocol.Command] = typedSystem
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
              security.Metrics.noop(),
              api.Metrics.noop(),
              persistence.Metrics.noop(),
              ops.Metrics.noop()
            ).flatten
          )

          override implicit val timeout: Timeout =
            rawConfig.getDuration("service.internal-query-timeout").toMillis.millis
        }
      }
    )
}
