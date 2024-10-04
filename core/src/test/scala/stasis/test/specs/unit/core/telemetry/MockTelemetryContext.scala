package stasis.test.specs.unit.core.telemetry

import stasis.layers.telemetry.metrics.MetricsProvider
import stasis.test.specs.unit.core.persistence.mocks.MockPersistenceMetrics
import stasis.test.specs.unit.core.routing.mocks.MockRoutingMetrics

class MockTelemetryContext extends stasis.layers.telemetry.MockTelemetryContext {
  override protected def providers(): Set[MetricsProvider] =
    super.providers() ++ Set(
      core.persistence.streaming,
      core.persistence.eventLog,
      core.persistence.manifest,
      core.persistence.reservation,
      core.routing.router
    )

  object core {
    object persistence {
      val streaming: MockPersistenceMetrics.StreamingBackend = MockPersistenceMetrics.StreamingBackend()
      val eventLog: MockPersistenceMetrics.EventLogBackend = MockPersistenceMetrics.EventLogBackend()
      val manifest: MockPersistenceMetrics.ManifestStore = MockPersistenceMetrics.ManifestStore()
      val reservation: MockPersistenceMetrics.ReservationStore = MockPersistenceMetrics.ReservationStore()
    }

    object routing {
      val router: MockRoutingMetrics.Router = MockRoutingMetrics.Router()
    }
  }
}

object MockTelemetryContext {
  def apply(): MockTelemetryContext = new MockTelemetryContext()
}
