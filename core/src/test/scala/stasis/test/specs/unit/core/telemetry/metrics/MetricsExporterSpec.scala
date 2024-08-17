package stasis.test.specs.unit.core.telemetry.metrics

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, StatusCodes}
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import io.opentelemetry.api.common.AttributeKey
import stasis.core.telemetry.metrics.MetricsExporter
import stasis.test.specs.unit.AsyncUnitSpec

import scala.collection.mutable
import scala.concurrent.Future

class MetricsExporterSpec extends AsyncUnitSpec {
  "A Prometheus MetricsExporter" should "provide a Prometheus metrics endpoint" in {
    import stasis.core.telemetry.metrics.MeterExtensions._

    val port = ports.dequeue()

    val exporter = MetricsExporter.Prometheus(instrumentation = "test", interface = "localhost", port = port)

    exporter.meter.counter(name = "counter_1").inc()
    exporter.meter.counter(name = "counter_2").inc(AttributeKey.stringKey("a") -> "b")

    for {
      metrics <- getMetrics(metricsUrl = s"http://localhost:$port/metrics")
    } yield {
      val _ = exporter.shutdown()
      metrics.filter(_.contains("counter")).sorted.toList match {
        case counter1 :: counter2 :: Nil =>
          counter1 should startWith("counter_1")
          counter2 should startWith("counter_2")

        case other =>
          fail(s"Unexpected result received: [$other]")
      }
    }
  }

  it should "support behaving as a proxy Prometheus registry" in {
    import stasis.core.telemetry.metrics.MeterExtensions._

    val port = ports.dequeue()

    val exporter = MetricsExporter.Prometheus.asProxyRegistry(instrumentation = "test", interface = "localhost", port = port) {
      registry =>
        import io.prometheus.client.Counter

        Counter
          .build("prometheus_counter_1", "Test description")
          .register(registry)
          .inc()

        Counter
          .build("prometheus_counter_2", "Test description")
          .labelNames("e")
          .register(registry)
          .labels("f")
          .inc()
    }

    exporter.meter.counter(name = "counter_3").inc()
    exporter.meter.counter(name = "counter_4").inc(AttributeKey.stringKey("c") -> "d")

    for {
      metrics <- getMetrics(metricsUrl = s"http://localhost:$port/metrics")
    } yield {
      val _ = exporter.shutdown()

      metrics.filter(_.contains("counter")).sorted.toList match {
        case counter3 :: counter4 :: prometheusCounter1 :: prometheusCounter2 :: Nil =>
          counter3 should startWith("counter_3")
          counter4 should startWith("counter_4")
          prometheusCounter1 should startWith("prometheus_counter_1")
          prometheusCounter2 should startWith("prometheus_counter_2")

        case other =>
          fail(s"Unexpected result received: [$other]")
      }
    }
  }

  private val ports: mutable.Queue[Int] = (38000 to 38100).to(mutable.Queue)

  private def getMetrics(
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

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "MetricsExporterSpec"
  )
}
