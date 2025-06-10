package stasis.client.service.components

import java.nio.file.Paths

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

import com.typesafe.{config => typesafe}
import org.apache.pekko.actor.CoordinatedShutdown
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout
import org.slf4j.Logger

import stasis.client.analysis.Checksum
import stasis.client.compression.Compression
import stasis.client.encryption.Aes
import stasis.client.encryption.{Decoder => EncryptionDecoder}
import stasis.client.encryption.{Encoder => EncryptionEncoder}
import stasis.client.ops
import stasis.client.service.ApplicationDirectory
import stasis.client.service.ApplicationTray
import stasis.client.service.components.internal.ConfigOverride
import stasis.client.service.components.internal.FutureOps
import stasis.client.staging.DefaultFileStaging
import stasis.client.staging.FileStaging
import stasis.core
import stasis.layers
import stasis.layers.telemetry.DefaultTelemetryContext
import stasis.layers.telemetry.TelemetryContext
import stasis.layers.telemetry.analytics.AnalyticsCollector
import stasis.layers.telemetry.analytics.DefaultAnalyticsCollector
import stasis.layers.telemetry.analytics.DefaultAnalyticsPersistence
import stasis.layers.telemetry.metrics.MetricsExporter
import stasis.layers.telemetry.metrics.MetricsProvider

trait Base extends FutureOps {
  implicit def system: ActorSystem[Nothing]
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
    typedSystem: ActorSystem[Nothing],
    logger: Logger
  ): Future[Base] =
    Future.fromTry(
      Try {
        new Base {
          override implicit val system: ActorSystem[Nothing] = typedSystem
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

          override implicit val telemetry: TelemetryContext = DefaultTelemetryContext(
            metricsProviders = Telemetry.loadMetricsProviders(
              metricsConfig = rawConfig.getConfig("service.telemetry.metrics")
            )(typedSystem),
            analyticsCollector = Telemetry.loadAnalyticsCollector(
              analyticsConfig = rawConfig.getConfig("service.telemetry.analytics"),
              directory = directory
            )(typedSystem, timeout)
          )

          override val terminateService: () => Unit = terminate
        }
      }
    )

  object Telemetry {
    final val Instrumentation: String = "stasis_client"

    def loadAnalyticsCollector(
      analyticsConfig: typesafe.Config,
      directory: ApplicationDirectory
    )(implicit system: ActorSystem[Nothing], timeout: Timeout): AnalyticsCollector =
      if (analyticsConfig.getBoolean("enabled")) {
        DefaultAnalyticsCollector(
          name = "analytics-collector",
          config = DefaultAnalyticsCollector.Config(
            persistenceInterval = analyticsConfig.getDuration("collector.persistence-interval").toMillis.millis,
            transmissionInterval = analyticsConfig.getDuration("collector.transmission-interval").toMillis.millis
          ),
          persistence = DefaultAnalyticsPersistence(
            config = DefaultAnalyticsPersistence.Config(
              localCache = directory.appDirectory.resolve(Files.AnalyticsCache),
              keepEvents = analyticsConfig.getBoolean("persistence.keep-events"),
              keepFailures = analyticsConfig.getBoolean("persistence.keep-failures")
            )
          ),
          app = stasis.client.BuildInfo
        )
      } else {
        AnalyticsCollector.NoOp
      }

    def loadMetricsProviders(
      metricsConfig: typesafe.Config
    )(implicit system: ActorSystem[Nothing]): Set[MetricsProvider] =
      if (metricsConfig.getBoolean("enabled")) {
        val exporter = createMetricsExporter(
          interface = metricsConfig.getString("interface"),
          port = metricsConfig.getInt("port")
        )

        Set(
          layers.security.Metrics.default(meter = exporter.meter, namespace = Instrumentation),
          layers.api.Metrics.default(meter = exporter.meter, namespace = Instrumentation),
          layers.persistence.Metrics.default(meter = exporter.meter, namespace = Instrumentation),
          core.persistence.Metrics.default(meter = exporter.meter, namespace = Instrumentation),
          ops.Metrics.default(meter = exporter.meter, namespace = Instrumentation)
        ).flatten
      } else {
        Set(
          layers.security.Metrics.noop(),
          layers.api.Metrics.noop(),
          layers.persistence.Metrics.noop(),
          core.persistence.Metrics.noop(),
          ops.Metrics.noop()
        ).flatten
      }

    private def createMetricsExporter(interface: String, port: Int)(implicit
      system: ActorSystem[Nothing]
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
