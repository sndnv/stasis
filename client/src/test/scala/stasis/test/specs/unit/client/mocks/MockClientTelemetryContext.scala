package stasis.test.specs.unit.client.mocks

import stasis.layers.telemetry.metrics.MetricsProvider

class MockClientTelemetryContext extends stasis.test.specs.unit.core.telemetry.MockTelemetryContext {
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
  def apply(): MockClientTelemetryContext = new MockClientTelemetryContext()
}
