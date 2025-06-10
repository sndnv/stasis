package stasis.layers.telemetry

import scala.reflect.ClassTag

import stasis.layers.telemetry.analytics.AnalyticsCollector
import stasis.layers.telemetry.metrics.MetricsProvider

class DefaultTelemetryContext(
  metricsProviders: Set[MetricsProvider],
  analyticsCollector: AnalyticsCollector
) extends TelemetryContext {
  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  override def metrics[M <: MetricsProvider](implicit tag: ClassTag[M]): M =
    metricsProviders.collectFirst { case provider: M => provider } match {
      case Some(provider) =>
        provider

      case None =>
        throw new IllegalStateException(
          s"Metrics provider [${tag.toString()}] requested but could not be found"
        )
    }

  override def analytics: AnalyticsCollector =
    analyticsCollector
}

object DefaultTelemetryContext {
  def apply(
    metricsProviders: Set[MetricsProvider],
    analyticsCollector: AnalyticsCollector
  ): DefaultTelemetryContext = new DefaultTelemetryContext(
    metricsProviders = metricsProviders,
    analyticsCollector = analyticsCollector
  )
}
