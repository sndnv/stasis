package stasis.test.specs.unit.client.service.components

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import org.slf4j.{Logger, LoggerFactory}
import stasis.client.analysis.Checksum
import stasis.client.compression.Gzip
import stasis.client.encryption.Aes
import stasis.client.ops
import stasis.client.ops.Metrics
import stasis.client.service.components.Base
import stasis.core.{api, persistence, security}
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers

import scala.concurrent.Future

class BaseSpec extends AsyncUnitSpec with ResourceHelpers {
  "A Base component" should "create itself from config" in {
    implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
      Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
      s"BaseSpec_${java.util.UUID.randomUUID().toString}"
    )

    Base(applicationDirectory = createApplicationDirectory(init = _ => ()), terminate = () => ()).map { base =>
      base.checksum should be(Checksum.CRC32)
      base.compression should be(Gzip)
      base.encryption should be(Aes)
    }
  }

  "A Base component telemetry" should "support providing metrics (no-op)" in {
    implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
      Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
      s"BaseSpec_${java.util.UUID.randomUUID().toString}"
    )

    val providers = Base.Telemetry.loadMetricsProviders(
      metricsConfig = typedSystem.settings.config.getConfig("stasis.test.client.service.telemetry.metrics-disabled")
    )

    providers should be(
      Set(
        security.Metrics.noop(),
        api.Metrics.noop(),
        persistence.Metrics.noop(),
        ops.Metrics.noop()
      ).flatten
    )
  }

  it should "support providing metrics (Prometheus)" in {
    implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
      Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
      s"BaseSpec_${java.util.UUID.randomUUID().toString}"
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
      metrics <- getMetrics(metricsUrl = s"http://localhost:19092")
      _ = typedSystem.terminate()
    } yield {
      metrics.filter(_.startsWith(Base.Telemetry.Instrumentation)) should not be empty
    }
  }

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)
}
