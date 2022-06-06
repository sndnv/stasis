package stasis.test.specs.unit.core.telemetry.metrics

import io.opentelemetry.api.common.{AttributeKey, Attributes}
import io.opentelemetry.api.metrics.{LongCounter, LongHistogram, LongUpDownCounter, MeterProvider}
import org.mockito.scalatest.MockitoSugar
import stasis.test.specs.unit.UnitSpec

class MeterExtensionsSpec extends UnitSpec with MockitoSugar {
  "MeterExtensions" should "support creating counters" in {
    import stasis.core.telemetry.metrics.MeterExtensions._

    val meter = MeterProvider.noop().get("MeterExtensionsSpec")
    meter.counter(name = "test-counter-1") should be(a[Counter])
    meter.counter(name = "test-counter-2", description = "test-description") should be(a[Counter])

    val mockCounter = mock[LongCounter]

    val counter = Counter(underlying = mockCounter)
    counter.add(value = 2)
    counter.add(value = 3, attributes = attributeKey -> "test-value")
    counter.inc()
    counter.inc(attributes = attributeKey -> "test-value")

    verify(mockCounter, times(4)).add(anyLong, any[Attributes])
  }

  they should "support creating histograms" in {
    import stasis.core.telemetry.metrics.MeterExtensions._

    val meter = MeterProvider.noop().get("MeterExtensionsSpec")
    meter.histogram(name = "test-histogram-1") should be(a[Histogram])
    meter.histogram(name = "test-histogram-2", description = "test-description") should be(a[Histogram])

    val mockHistogram = mock[LongHistogram]

    val histogram = Histogram(underlying = mockHistogram)
    histogram.record(value = 2)
    histogram.record(value = 3, attributes = attributeKey -> "test-value")

    verify(mockHistogram, times(2)).record(anyLong, any[Attributes])
  }

  they should "support creating up-down counters" in {
    import stasis.core.telemetry.metrics.MeterExtensions._

    val meter = MeterProvider.noop().get("MeterExtensionsSpec")
    meter.upDownCounter(name = "test-counter-1") should be(a[UpDownCounter])
    meter.upDownCounter(name = "test-counter-2", description = "test-description") should be(a[UpDownCounter])

    val mockCounter = mock[LongUpDownCounter]

    val counter = UpDownCounter(underlying = mockCounter)
    counter.add(value = 2)
    counter.add(value = 3, attributes = attributeKey -> "test-value")
    counter.inc()
    counter.inc(attributes = attributeKey -> "test-value")
    counter.dec()

    verify(mockCounter, times(5)).add(anyLong, any[Attributes])
  }

  private val attributeKey: AttributeKey[String] = AttributeKey.stringKey("test-key")
}
