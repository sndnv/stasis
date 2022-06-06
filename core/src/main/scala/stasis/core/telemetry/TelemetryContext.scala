package stasis.core.telemetry

import stasis.core.telemetry.metrics.MetricsProvider

import scala.reflect.ClassTag

trait TelemetryContext {
  def metrics[M <: MetricsProvider](implicit tag: ClassTag[M]): M
}
