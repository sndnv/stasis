package stasis.test.specs.unit.client.mocks

import stasis.core.telemetry.metrics.MetricsProvider
import stasis.core.telemetry.{DefaultTelemetryContext, TelemetryContext}
import stasis.test.specs.unit.core.telemetry.mocks._

import scala.reflect.ClassTag

class MockClientTelemetryContext extends TelemetryContext {
  private lazy val underlying = DefaultTelemetryContext(
    metricsProviders = Set(
      persistence.streaming,
      persistence.keyValue,
      persistence.eventLog,
      security.oauthClient,
      ops.backup,
      ops.recovery
    )
  )

  object persistence {
    val streaming: MockPersistenceMetrics.StreamingBackend = MockPersistenceMetrics.StreamingBackend()
    val keyValue: MockPersistenceMetrics.KeyValueBackend = MockPersistenceMetrics.KeyValueBackend()
    val eventLog: MockPersistenceMetrics.EventLogBackend = MockPersistenceMetrics.EventLogBackend()
  }

  object security {
    val oauthClient: MockSecurityMetrics.OAuthClient = MockSecurityMetrics.OAuthClient()
  }

  object ops {
    val backup: MockOpsMetrics.BackupOperation = MockOpsMetrics.BackupOperation()
    val recovery: MockOpsMetrics.RecoveryOperation = MockOpsMetrics.RecoveryOperation()
  }

  override def metrics[M <: MetricsProvider](implicit tag: ClassTag[M]): M = underlying.metrics[M](tag)
}

object MockClientTelemetryContext {
  def apply(): MockClientTelemetryContext = new MockClientTelemetryContext()
}
