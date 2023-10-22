package stasis.test.specs.unit.core.api

import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse}
import stasis.core.api.Metrics
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.core.telemetry.mocks.MockMeter

class MetricsSpec extends UnitSpec {
  "Metrics" should "provide a no-op implementation" in {
    Metrics.noop() should be(Set(Metrics.Endpoint.NoOp))

    val metrics = Metrics.Endpoint.NoOp

    noException should be thrownBy metrics.recordRequest(
      request = null
    )

    noException should be thrownBy metrics.recordResponse(
      requestStart = 0,
      request = null,
      response = null
    )
  }

  they should "provide a default implementation" in {
    val meter = MockMeter()
    val metrics = new Metrics.Endpoint.Default(meter = meter, namespace = "test")

    metrics.recordRequest(request = HttpRequest())
    metrics.recordRequest(request = HttpRequest())
    metrics.recordResponse(requestStart = 0, request = HttpRequest(), response = HttpResponse())

    meter.metric(name = "test_endpoints_requests") should be(2)
    meter.metric(name = "test_endpoints_response_times") should be(1)
  }
}
