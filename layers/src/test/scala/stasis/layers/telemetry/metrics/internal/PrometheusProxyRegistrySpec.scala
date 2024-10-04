package stasis.layers.telemetry.metrics.internal

import java.util
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

import scala.jdk.CollectionConverters._

import io.opentelemetry.sdk.metrics.InstrumentType
import io.opentelemetry.sdk.metrics.data.AggregationTemporality
import io.prometheus.client.Collector

import stasis.layers.UnitSpec
import stasis.layers.telemetry.mocks.MockMeter

class PrometheusProxyRegistrySpec extends UnitSpec {
  "A PrometheusProxyRegistry" should "support gauge and counter metrics" in {
    val meter = MockMeter()
    val collected = new AtomicInteger(0)

    val proxy = PrometheusProxyRegistry(meter)

    proxy.register { () =>
      val gauge = new Collector.MetricFamilySamples(
        "test_gauge",
        Collector.Type.GAUGE,
        "Test description",
        util.List.of[Collector.MetricFamilySamples.Sample](
          sample(value = 1.0d, labels = "a" -> "1", "b" -> "test"),
          sample(value = 2.0d, labels = "c" -> "d")
        )
      )

      val counter = new Collector.MetricFamilySamples(
        "test_counter",
        Collector.Type.COUNTER,
        "Test description",
        util.List.of[Collector.MetricFamilySamples.Sample](
          sample(value = 2.0d)
        )
      )

      val _ = collected.incrementAndGet()

      util.List.of(gauge, counter)
    }

    noException should be thrownBy proxy.register(registration = null)
    proxy.forceFlush().isSuccess should be(true)
    proxy.shutdown().isSuccess should be(true)
    proxy.getAggregationTemporality(instrumentType = InstrumentType.COUNTER) should be(AggregationTemporality.CUMULATIVE)
    proxy.getAggregationTemporality(instrumentType = InstrumentType.UP_DOWN_COUNTER) should be(AggregationTemporality.CUMULATIVE)

    collected.get() should be(1) // initial collection
    meter.metric("test_gauge") should be(0)
    meter.metric("test_counter") should be(0)

    meter.collect()
    collected.get() should be(3) // + one call per metric (2 in total)
    meter.metric("test_gauge") should be(2) // + two samples
    meter.metric("test_counter") should be(1)

    meter.collect()
    collected.get() should be(5) // + one call per metric (2 in total)
    meter.metric("test_gauge") should be(4) // + two samples
    meter.metric("test_counter") should be(2)
  }

  it should "skip metrics with unsupported types" in {
    val meter = MockMeter()
    val collected = new AtomicInteger(0)

    val proxy = PrometheusProxyRegistry(meter)

    proxy.register { () =>
      val counter = new Collector.MetricFamilySamples(
        "test_counter",
        Collector.Type.COUNTER,
        "Test description",
        util.List.of[Collector.MetricFamilySamples.Sample](
          sample(value = 2.0d)
        )
      )

      val histogram = new Collector.MetricFamilySamples(
        "test_histogram",
        Collector.Type.HISTOGRAM,
        "Test description",
        util.List.of[Collector.MetricFamilySamples.Sample](
          sample(value = 2.0d)
        )
      )

      val _ = collected.incrementAndGet()

      util.List.of(counter, histogram)
    }

    collected.get() should be(1) // initial collection
    meter.metric("test_counter") should be(0)
    an[IllegalArgumentException] should be thrownBy meter.metric("test_histogram")

    meter.collect()
    collected.get() should be(2) // one call for the one supported metric
    meter.metric("test_counter") should be(1)
    an[IllegalArgumentException] should be thrownBy meter.metric("test_histogram")
  }

  private def sample(value: Double, labels: (String, String)*): Collector.MetricFamilySamples.Sample =
    new Collector.MetricFamilySamples.Sample(
      UUID.randomUUID().toString,
      labels.map(_._1).asJava,
      labels.map(_._2).asJava,
      value
    )
}
