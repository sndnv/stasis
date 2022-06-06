package stasis.core.telemetry.metrics

import io.opentelemetry.api.common.{AttributeKey, Attributes}
import io.opentelemetry.api.metrics._

object MeterExtensions {
  implicit class ExtendedMeter(meter: Meter) {
    def counter(name: String): Counter =
      Counter(meter.counterBuilder(name).build())

    def counter(name: String, description: String): Counter =
      Counter(meter.counterBuilder(name).setDescription(description).build())

    def histogram(name: String, description: String): Histogram =
      Histogram(meter.histogramBuilder(name).ofLongs().setDescription(description).build())

    def histogram(name: String): Histogram =
      Histogram(meter.histogramBuilder(name).ofLongs().build())

    def upDownCounter(name: String): UpDownCounter =
      UpDownCounter(meter.upDownCounterBuilder(name).build())

    def upDownCounter(name: String, description: String): UpDownCounter =
      UpDownCounter(meter.upDownCounterBuilder(name).setDescription(description).build())
  }

  final case class Counter(underlying: LongCounter) extends AnyVal {
    def add(value: Long, attributes: (AttributeKey[String], String)*): Unit =
      underlying.add(value, attributes.combine)

    def inc(attributes: (AttributeKey[String], String)*): Unit =
      underlying.add(1L, attributes.combine)
  }

  final case class Histogram(underlying: LongHistogram) extends AnyVal {
    def record(value: Long, attributes: (AttributeKey[String], String)*): Unit =
      underlying.record(value, attributes.combine)
  }

  final case class UpDownCounter(underlying: LongUpDownCounter) extends AnyVal {
    def add(value: Long, attributes: (AttributeKey[String], String)*): Unit =
      underlying.add(value, attributes.combine)

    def inc(attributes: (AttributeKey[String], String)*): Unit =
      underlying.add(1L, attributes.combine)

    def dec(attributes: (AttributeKey[String], String)*): Unit =
      underlying.add(-1L, attributes.combine)
  }

  private implicit class ExtendedAttributes(attributes: Seq[(AttributeKey[String], String)]) {
    def combine: Attributes = attributes
      .foldLeft(Attributes.builder()) { case (builder, (key, value)) =>
        builder.put(key, value)
      }
      .build()
  }
}
