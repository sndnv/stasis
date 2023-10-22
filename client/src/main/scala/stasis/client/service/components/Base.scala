package stasis.client.service.components

import org.apache.pekko.actor.CoordinatedShutdown
import org.apache.pekko.actor.typed.{ActorSystem, SpawnProtocol}
import org.apache.pekko.util.Timeout
import com.typesafe.{config => typesafe}
import org.slf4j.Logger
import stasis.client.analysis.Checksum
import stasis.client.compression.Compression
import stasis.client.encryption.{Aes, Decoder => EncryptionDecoder, Encoder => EncryptionEncoder}
import stasis.client.ops
import stasis.client.service.{ApplicationDirectory, ApplicationTray}
import stasis.client.service.components.internal.{ConfigOverride, FutureOps}
import stasis.client.staging.{DefaultFileStaging, FileStaging}
import stasis.core.telemetry.metrics.{MetricsExporter, MetricsProvider}
import stasis.core.telemetry.{DefaultTelemetryContext, TelemetryContext}
import stasis.core.{api, persistence, security}

import java.nio.file.Paths
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait Base extends FutureOps {
  implicit def system: ActorSystem[SpawnProtocol.Command]
  implicit def ec: ExecutionContext
  implicit def log: Logger
  implicit def telemetry: TelemetryContext

  def directory: ApplicationDirectory
  def tray: ApplicationTray

  def configOverride: typesafe.Config
  def rawConfig: typesafe.Config

  implicit def timeout: Timeout
  def terminationDelay: FiniteDuration

  def checksum: Checksum
  def compression: Compression
  def encryption: EncryptionEncoder with EncryptionDecoder
  def staging: FileStaging

  def terminateService: () => Unit
}

object Base {
  def apply(
    applicationDirectory: ApplicationDirectory,
    applicationTray: ApplicationTray,
    terminate: () => Unit
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

          override val directory: ApplicationDirectory =
            applicationDirectory

          override val tray: ApplicationTray =
            applicationTray

          override val configOverride: typesafe.Config =
            ConfigOverride.load(directory)

          override val rawConfig: typesafe.Config =
            configOverride.withFallback(system.settings.config).getConfig("stasis.client").resolve()

          override implicit val telemetry: TelemetryContext = DefaultTelemetryContext(
            metricsProviders = Telemetry.loadMetricsProviders(
              metricsConfig = rawConfig.getConfig("service.telemetry.metrics")
            )(typedSystem)
          )

          override implicit val timeout: Timeout =
            rawConfig.getDuration("service.internal-query-timeout").toMillis.millis

          override val terminationDelay: FiniteDuration =
            rawConfig.getDuration("service.termination-delay").toMillis.millis

          override val checksum: Checksum =
            Checksum(rawConfig.getString("analysis.checksum"))

          override val compression: Compression =
            Compression(
              withDefaultCompression = rawConfig.getString("compression.default"),
              withDisabledExtensions = rawConfig.getString("compression.disabled-extensions.list")
            )

          override val encryption: EncryptionEncoder with EncryptionDecoder =
            Aes

          override val staging: FileStaging =
            new DefaultFileStaging(
              storeDirectory = Try(rawConfig.getString("staging.store-directory")).toOption.map(Paths.get(_)),
              prefix = rawConfig.getString("staging.files.prefix"),
              suffix = rawConfig.getString("staging.files.suffix")
            )

          override val terminateService: () => Unit = terminate
        }
      }
    )

  object Telemetry {
    final val Instrumentation: String = "stasis_client"

    def loadMetricsProviders(
      metricsConfig: typesafe.Config
    )(implicit system: ActorSystem[SpawnProtocol.Command]): Set[MetricsProvider] =
      if (metricsConfig.getBoolean("enabled")) {
        val exporter = createMetricsExporter(
          interface = metricsConfig.getString("interface"),
          port = metricsConfig.getInt("port")
        )

        Set(
          security.Metrics.default(meter = exporter.meter, namespace = Instrumentation),
          api.Metrics.default(meter = exporter.meter, namespace = Instrumentation),
          persistence.Metrics.default(meter = exporter.meter, namespace = Instrumentation),
          ops.Metrics.default(meter = exporter.meter, namespace = Instrumentation)
        ).flatten
      } else {
        Set(
          security.Metrics.noop(),
          api.Metrics.noop(),
          persistence.Metrics.noop(),
          ops.Metrics.noop()
        ).flatten
      }

    private def createMetricsExporter(interface: String, port: Int)(implicit
      system: ActorSystem[SpawnProtocol.Command]
    ): MetricsExporter = {
      val exporter = MetricsExporter.Prometheus.apply(
        instrumentation = Instrumentation,
        interface = interface,
        port = port
      )

      CoordinatedShutdown(system).addTask(
        phase = CoordinatedShutdown.PhaseServiceStop,
        taskName = "metricsExporterShutdown"
      ) { () => exporter.shutdown() }

      exporter
    }
  }
}
