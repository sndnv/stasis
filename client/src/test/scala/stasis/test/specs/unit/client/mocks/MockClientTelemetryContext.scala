package stasis.test.specs.unit.client.mocks

import io.github.sndnv.layers.telemetry.analytics.AnalyticsCollector
import io.github.sndnv.layers.telemetry.metrics.MetricsProvider

class MockClientTelemetryContext(
  collector: Option[AnalyticsCollector]
) extends stasis.test.specs.unit.core.telemetry.MockTelemetryContext(collector) {
  override protected def providers(): Set[MetricsProvider] =
    super.providers() ++ Set(
      client.ops.backup,
      client.ops.recovery
    )

  object client {
    object ops {
      val backup: MockOpsMetrics.BackupOperation = MockOpsMetrics.BackupOperation()
      val recovery: MockOpsMetrics.RecoveryOperation = MockOpsMetrics.RecoveryOperation()
    }
  }
}

object MockClientTelemetryContext {
  def apply(): MockClientTelemetryContext =
    new MockClientTelemetryContext(collector = None)

  def apply(collector: AnalyticsCollector): MockClientTelemetryContext =
    new MockClientTelemetryContext(collector = Some(collector))
}
