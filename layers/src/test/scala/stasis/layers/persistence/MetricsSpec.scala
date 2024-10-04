package stasis.layers.persistence

import stasis.layers.UnitSpec
import stasis.layers.telemetry.mocks.MockMeter

class MetricsSpec extends UnitSpec {
  "Metrics" should "provide a no-op implementation" in {
    Metrics.noop() should be(
      Set(
        Metrics.Store.NoOp
      )
    )

    val keyValueMetrics = Metrics.Store.NoOp
    noException should be thrownBy keyValueMetrics.recordPut(store = null)
    noException should be thrownBy keyValueMetrics.recordGet(store = null)
    noException should be thrownBy keyValueMetrics.recordGet(store = null, entries = 0)
    noException should be thrownBy keyValueMetrics.recordDelete(store = null)
  }

  they should "provide a default implementation" in {
    val meter = MockMeter()

    val keyValueMetrics = new Metrics.Store.Default(meter = meter, namespace = "test")
    keyValueMetrics.recordPut(store = "test")
    keyValueMetrics.recordPut(store = "test")
    keyValueMetrics.recordPut(store = "test")
    keyValueMetrics.recordGet(store = "test")
    keyValueMetrics.recordGet(store = "test", entries = 4)
    keyValueMetrics.recordDelete(store = "test")
    keyValueMetrics.recordDelete(store = "test")

    meter.metric(name = "test_persistence_store_put_operations") should be(3)
    meter.metric(name = "test_persistence_store_get_operations") should be(2)
    meter.metric(name = "test_persistence_store_delete_operations") should be(2)
  }
}
