package stasis.test.specs.unit.core.telemetry

import stasis.layers.telemetry.analytics.AnalyticsCollector
import stasis.layers.telemetry.metrics.MetricsProvider
import stasis.test.specs.unit.core.persistence.MockPersistenceMetrics
import stasis.test.specs.unit.core.routing.mocks.MockRoutingMetrics

class MockTelemetryContext(collector: Option[AnalyticsCollector])
    extends stasis.layers.telemetry.MockTelemetryContext(collector) {
  override protected def providers(): Set[MetricsProvider] =
    super.providers() ++ Set(
      core.persistence.streaming,
      core.persistence.eventLog,
      core.persistence.manifest,
      core.persistence.reservation,
      core.persistence.command,
      core.routing.router
    )

  object core {
    object persistence {
      val streaming: MockPersistenceMetrics.StreamingBackend = MockPersistenceMetrics.StreamingBackend()
      val eventLog: MockPersistenceMetrics.EventLogBackend = MockPersistenceMetrics.EventLogBackend()
      val manifest: MockPersistenceMetrics.ManifestStore = MockPersistenceMetrics.ManifestStore()
      val reservation: MockPersistenceMetrics.ReservationStore = MockPersistenceMetrics.ReservationStore()
      val command: MockPersistenceMetrics.CommandStore = MockPersistenceMetrics.CommandStore()
    }

    object routing {
      val router: MockRoutingMetrics.Router = MockRoutingMetrics.Router()
    }
  }
}

object MockTelemetryContext {
  def apply(): MockTelemetryContext =
    new MockTelemetryContext(collector = None)

  def apply(collector: AnalyticsCollector): MockTelemetryContext =
    new MockTelemetryContext(collector = Some(collector))
}
