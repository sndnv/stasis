package stasis.test.specs.unit.core.telemetry

import stasis.core.api.{Metrics => ApiMetrics}
import stasis.core.routing.{Metrics => RoutingMetrics}
import stasis.core.telemetry.DefaultTelemetryContext
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.core.telemetry.mocks.{MockApiMetrics, MockRoutingMetrics}

class DefaultTelemetryContextSpec extends UnitSpec {
  "A DefaultTelemetryContext" should "provide metrics" in {
    val context = DefaultTelemetryContext(
      metricsProviders = Set(
        MockApiMetrics.Endpoint(),
        MockRoutingMetrics.Router()
      )
    )

    noException should be thrownBy context.metrics[ApiMetrics.Endpoint]
    noException should be thrownBy context.metrics[RoutingMetrics.Router]
  }

  it should "fail if a requested provider is not available" in {
    val context = DefaultTelemetryContext(
      metricsProviders = Set(
        MockApiMetrics.Endpoint()
      )
    )

    noException should be thrownBy context.metrics[ApiMetrics.Endpoint]
    an[IllegalStateException] should be thrownBy context.metrics[RoutingMetrics.Router]
  }
}
