package stasis.layers.telemetry.metrics.internal

import scala.jdk.CollectionConverters._

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.metrics.InstrumentType
import io.opentelemetry.sdk.metrics.`export`.CollectionRegistration
import io.opentelemetry.sdk.metrics.`export`.MetricReader
import io.opentelemetry.sdk.metrics.data.AggregationTemporality
import io.prometheus.client.Collector
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.SampleNameFilter
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PrometheusProxyRegistry(meter: => Meter) extends CollectorRegistry(true) with MetricReader {
  import PrometheusProxyRegistry.ExtendedCollector

  private val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  override def register(collector: Collector): Unit =
    collector.collect().asScala.foreach { metric =>
      metric.`type` match {
        case Collector.Type.GAUGE =>
          meter
            .upDownCounterBuilder(metric.name)
            .setDescription(metric.help)
            .ofDoubles()
            .buildWithCallback { measurement =>
              collector.measure(metric = metric.name, withMeasurement = measurement)
            }

        case Collector.Type.COUNTER =>
          meter
            .counterBuilder(metric.name)
            .setDescription(metric.help)
            .ofDoubles()
            .buildWithCallback { measurement =>
              collector.measure(metric = metric.name, withMeasurement = measurement)
            }

        case other =>
          log.debug("Skipping metric [{}] with unsupported type: [{}]", metric.name, other.name())
      }
    }

  override def register(registration: CollectionRegistration): Unit =
    ()

  override def forceFlush(): CompletableResultCode =
    CompletableResultCode.ofSuccess()

  override def shutdown(): CompletableResultCode =
    CompletableResultCode.ofSuccess()

  override def getAggregationTemporality(instrumentType: InstrumentType): AggregationTemporality =
    AggregationTemporality.CUMULATIVE
}

object PrometheusProxyRegistry {
  def apply(meter: => Meter): PrometheusProxyRegistry =
    new PrometheusProxyRegistry(meter)

  private implicit class ExtendedCollector(collector: Collector) {
    def measure(metric: String, withMeasurement: ObservableDoubleMeasurement): Unit =
      collector
        .collect(new SampleNameFilter.Builder().nameMustBeEqualTo(metric).build())
        .asScala
        .foreach { result =>
          result.samples.asScala.filterNot(_.name.endsWith("_created")).foreach { sample =>
            val attributes = sample.labelNames.asScala
              .zip(sample.labelValues.asScala)
              .foldLeft(Attributes.builder()) { case (builder, (name, value)) =>
                builder.put(name, value)
              }
              .build()

            withMeasurement.record(sample.value, attributes)
          }
        }
  }
}
