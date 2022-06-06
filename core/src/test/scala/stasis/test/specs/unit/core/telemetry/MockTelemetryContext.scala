package stasis.test.specs.unit.core.telemetry

import stasis.core.telemetry.{DefaultTelemetryContext, TelemetryContext}
import stasis.core.telemetry.metrics.MetricsProvider
import stasis.test.specs.unit.core.telemetry.mocks._

import scala.reflect.ClassTag

class MockTelemetryContext extends TelemetryContext {
  private lazy val underlying = DefaultTelemetryContext(
    metricsProviders = Set(
      api.endpoint,
      persistence.streaming,
      persistence.keyValue,
      persistence.eventLog,
      persistence.manifest,
      persistence.reservation,
      routing.router,
      security.authenticator,
      security.keyProvider,
      security.oauthClient
    )
  )

  object api {
    val endpoint: MockApiMetrics.Endpoint = MockApiMetrics.Endpoint()
  }

  object persistence {
    val streaming: MockPersistenceMetrics.StreamingBackend = MockPersistenceMetrics.StreamingBackend()
    val keyValue: MockPersistenceMetrics.KeyValueBackend = MockPersistenceMetrics.KeyValueBackend()
    val eventLog: MockPersistenceMetrics.EventLogBackend = MockPersistenceMetrics.EventLogBackend()
    val manifest: MockPersistenceMetrics.ManifestStore = MockPersistenceMetrics.ManifestStore()
    val reservation: MockPersistenceMetrics.ReservationStore = MockPersistenceMetrics.ReservationStore()
  }

  object routing {
    val router: MockRoutingMetrics.Router = MockRoutingMetrics.Router()
  }

  object security {
    val authenticator: MockSecurityMetrics.Authenticator = MockSecurityMetrics.Authenticator()
    val keyProvider: MockSecurityMetrics.KeyProvider = MockSecurityMetrics.KeyProvider()
    val oauthClient: MockSecurityMetrics.OAuthClient = MockSecurityMetrics.OAuthClient()
  }

  override def metrics[M <: MetricsProvider](implicit tag: ClassTag[M]): M = underlying.metrics[M](tag)
}

object MockTelemetryContext {
  def apply(): MockTelemetryContext = new MockTelemetryContext()
}
