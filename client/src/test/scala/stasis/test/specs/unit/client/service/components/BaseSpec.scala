package stasis.test.specs.unit.client.service.components

import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.HttpMethods
import org.apache.pekko.http.scaladsl.model.HttpRequest
import org.apache.pekko.http.scaladsl.model.HttpResponse
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import org.apache.pekko.util.Timeout
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.client.analysis.Checksum
import stasis.client.compression.Gzip
import stasis.client.encryption.Aes
import stasis.client.ops
import stasis.client.ops.Metrics
import stasis.client.service.ApplicationTray
import stasis.client.service.components.Base
import stasis.core
import io.github.sndnv.layers
import io.github.sndnv.layers.telemetry.analytics.AnalyticsCollector
import io.github.sndnv.layers.telemetry.analytics.DefaultAnalyticsCollector
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers

class BaseSpec extends AsyncUnitSpec with ResourceHelpers {
  "A Base component" should "create itself from config" in {
    implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
      guardianBehavior = Behaviors.ignore,
      name = s"BaseSpec_${java.util.UUID.randomUUID().toString}"
    )

    Base(
      applicationDirectory = createApplicationDirectory(init = _ => ()),
      applicationTray = ApplicationTray.NoOp(),
      terminate = () => ()
    ).map { base =>
      base.checksum should be(Checksum.CRC32)
      base.compression.defaultCompression should be(Gzip)
      base.encryption should be(Aes)
    }
  }

  "A Base component telemetry" should "support providing an analytics collector (disabled)" in {
    implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
      guardianBehavior = Behaviors.ignore,
      name = s"BaseSpec_${java.util.UUID.randomUUID().toString}"
    )

    implicit val timeout: Timeout = 3.seconds

    val collector = Base.Telemetry.loadAnalyticsCollector(
      analyticsConfig = typedSystem.settings.config.getConfig("stasis.test.client.service.telemetry.analytics-disabled"),
      directory = createApplicationDirectory(init = _ => ())
    )

    collector should be(a[AnalyticsCollector.NoOp.type])
  }

  it should "support providing an analytics collector (enabled)" in {
    implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
      guardianBehavior = Behaviors.ignore,
      name = s"BaseSpec_${java.util.UUID.randomUUID().toString}"
    )

    implicit val timeout: Timeout = 3.seconds

    val collector = Base.Telemetry.loadAnalyticsCollector(
      analyticsConfig = typedSystem.settings.config.getConfig("stasis.test.client.service.telemetry.analytics-enabled"),
      directory = createApplicationDirectory(init = _ => ())
    )

    collector should be(a[DefaultAnalyticsCollector])
  }

  it should "support providing metrics (no-op)" in {
    implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
      guardianBehavior = Behaviors.ignore,
      name = s"BaseSpec_${java.util.UUID.randomUUID().toString}"
    )

    val providers = Base.Telemetry.loadMetricsProviders(
      metricsConfig = typedSystem.settings.config.getConfig("stasis.test.client.service.telemetry.metrics-disabled")
    )

    providers should be(
      Set(
        layers.security.Metrics.noop(),
        layers.api.Metrics.noop(),
        layers.persistence.Metrics.noop(),
        core.persistence.Metrics.noop(),
        ops.Metrics.noop()
      ).flatten
    )
  }

  it should "support providing metrics (Prometheus)" in {
    implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
      guardianBehavior = Behaviors.ignore,
      name = s"BaseSpec_${java.util.UUID.randomUUID().toString}"
    )

    val providers = Base.Telemetry.loadMetricsProviders(
      metricsConfig = typedSystem.settings.config.getConfig("stasis.test.client.service.telemetry.metrics-enabled")
    )

    providers.collectFirst { case metrics: Metrics.BackupOperation.Default => metrics } match {
      case Some(metrics) => metrics.recordEntityChunkProcessed(step = "test", bytes = 1)
      case None          => fail("Expected backup operation metrics but none were found")
    }

    def getMetrics(
      metricsUrl: String
    ): Future[Seq[String]] =
      Http()
        .singleRequest(
          request = HttpRequest(
            method = HttpMethods.GET,
            uri = metricsUrl
          )
        )
        .flatMap {
          case HttpResponse(StatusCodes.OK, _, entity, _) => Unmarshal(entity).to[String]
          case response                                   => fail(s"Unexpected response received: [$response]")
        }
        .map { result =>
          result.split("\n").toSeq.filterNot(_.startsWith("#"))
        }

    for {
      metrics <- getMetrics(metricsUrl = s"http://localhost:19092/metrics")
      _ = typedSystem.terminate()
    } yield {
      metrics.filter(_.startsWith(Base.Telemetry.Instrumentation)) should not be empty
    }
  }

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)
}
