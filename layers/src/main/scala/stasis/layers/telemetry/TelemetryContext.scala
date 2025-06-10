package stasis.layers.telemetry

import scala.reflect.ClassTag

import stasis.layers.telemetry.analytics.AnalyticsCollector
import stasis.layers.telemetry.metrics.MetricsProvider

trait TelemetryContext {
  def metrics[M <: MetricsProvider](implicit tag: ClassTag[M]): M
  def analytics: AnalyticsCollector
}
