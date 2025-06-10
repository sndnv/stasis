package stasis.layers.telemetry

import scala.reflect.ClassTag

import stasis.layers.telemetry.analytics.AnalyticsCollector
import stasis.layers.telemetry.metrics.MetricsProvider
import stasis.layers.telemetry.mocks.MockApiMetrics
import stasis.layers.telemetry.mocks.MockApiMetrics.Endpoint
import stasis.layers.telemetry.mocks._

class MockTelemetryContext(collector: Option[AnalyticsCollector]) extends TelemetryContext {
  protected def providers(): Set[MetricsProvider] = Set(
    layers.api.endpoint,
    layers.persistence.keyValue,
    layers.security.authenticator,
    layers.security.keyProvider,
    layers.security.oauthClient
  )

  private lazy val underlying = new DefaultTelemetryContext(
    metricsProviders = providers(),
    analyticsCollector = collector.getOrElse(AnalyticsCollector.NoOp)
  )

  object layers {
    object api {
      val endpoint: Endpoint = MockApiMetrics.Endpoint()
    }

    object persistence {
      val keyValue: MockPersistenceMetrics.KeyValueStore = MockPersistenceMetrics.KeyValueStore()
    }

    object security {
      val authenticator: MockSecurityMetrics.Authenticator = MockSecurityMetrics.Authenticator()
      val keyProvider: MockSecurityMetrics.KeyProvider = MockSecurityMetrics.KeyProvider()
      val oauthClient: MockSecurityMetrics.OAuthClient = MockSecurityMetrics.OAuthClient()
    }
  }

  override def metrics[M <: MetricsProvider](implicit tag: ClassTag[M]): M =
    underlying.metrics[M](tag)

  override def analytics: AnalyticsCollector =
    underlying.analytics
}

object MockTelemetryContext {
  def apply(): MockTelemetryContext =
    new MockTelemetryContext(collector = None)

  def apply(collector: AnalyticsCollector): MockTelemetryContext =
    new MockTelemetryContext(collector = Some(collector))
}
