package stasis.test.specs.unit.core.routing

import stasis.core.routing.Metrics
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.core.telemetry.mocks.MockMeter

class MetricsSpec extends UnitSpec {
  "Metrics" should "provide a no-op implementation" in {
    Metrics.noop() should be(Set(Metrics.Router.NoOp))

    val metrics = Metrics.Router.NoOp

    noException should be thrownBy metrics.recordPush(router = null, bytes = 0L)
    noException should be thrownBy metrics.recordPushFailure(router = null, reason = null)
    noException should be thrownBy metrics.recordPull(router = null, bytes = 0L)
    noException should be thrownBy metrics.recordPullFailure(router = null, reason = null)
    noException should be thrownBy metrics.recordDiscard(router = null, bytes = 0L)
    noException should be thrownBy metrics.recordDiscardFailure(router = null, reason = null)
    noException should be thrownBy metrics.recordReserve(router = null, bytes = 0L)
    noException should be thrownBy metrics.recordReserveFailure(router = null, reason = null)
    noException should be thrownBy metrics.recordStage(router = null, bytes = 0L)
  }

  they should "provide a default implementation" in {
    val meter = MockMeter()

    val metrics = new Metrics.Router.Default(meter = meter, namespace = "test")

    metrics.recordPush(router = "test", bytes = 1L)
    metrics.recordPush(router = "test", bytes = 2L)
    metrics.recordPush(router = "test", bytes = 3L)
    metrics.recordPushFailure(router = "test", reason = Metrics.Router.PushFailure.NoSinks)
    metrics.recordPull(router = "test", bytes = 4L)
    metrics.recordPullFailure(router = "test", reason = Metrics.Router.PullFailure.NoNodes)
    metrics.recordPullFailure(router = "test", reason = Metrics.Router.PullFailure.NoContent)
    metrics.recordDiscard(router = "test", bytes = 5L)
    metrics.recordDiscardFailure(router = "test", reason = Metrics.Router.DiscardFailure.NoManifest)
    metrics.recordReserve(router = "test", bytes = 6L)
    metrics.recordReserveFailure(router = "test", reason = Metrics.Router.ReserveFailure.NoStorage)
    metrics.recordReserveFailure(router = "test", reason = Metrics.Router.ReserveFailure.ReservationExists)
    metrics.recordStage(router = "test", bytes = 7L)

    meter.metric(name = "test_routers_push_operations") should be(3)
    meter.metric(name = "test_routers_push_operation_failures") should be(1)
    meter.metric(name = "test_routers_push_bytes") should be(3)
    meter.metric(name = "test_routers_pull_operations") should be(1)
    meter.metric(name = "test_routers_pull_operation_failures") should be(2)
    meter.metric(name = "test_routers_pull_bytes") should be(1)
    meter.metric(name = "test_routers_discard_operations") should be(1)
    meter.metric(name = "test_routers_discard_operation_failures") should be(1)
    meter.metric(name = "test_routers_discard_bytes") should be(1)
    meter.metric(name = "test_routers_reserve_operations") should be(1)
    meter.metric(name = "test_routers_reserve_operation_failures") should be(2)
    meter.metric(name = "test_routers_reserve_bytes") should be(1)
    meter.metric(name = "test_routers_stage_operations") should be(1)
    meter.metric(name = "test_routers_stage_bytes") should be(1)
  }
}
