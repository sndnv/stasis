package stasis.test.specs.unit.core.persistence

import stasis.core.persistence.Metrics
import stasis.core.routing.Node
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.core.telemetry.mocks.MockMeter

class MetricsSpec extends UnitSpec {
  "Metrics" should "provide a no-op implementation" in {
    Metrics.noop() should be(
      Set(
        Metrics.StreamingBackend.NoOp,
        Metrics.KeyValueBackend.NoOp,
        Metrics.EventLogBackend.NoOp,
        Metrics.ManifestStore.NoOp,
        Metrics.ReservationStore.NoOp
      )
    )

    val streamingMetrics = Metrics.StreamingBackend.NoOp
    noException should be thrownBy streamingMetrics.recordInit(backend = null)
    noException should be thrownBy streamingMetrics.recordDrop(backend = null)
    noException should be thrownBy streamingMetrics.recordWrite(backend = null, bytes = 0)
    noException should be thrownBy streamingMetrics.recordRead(backend = null, bytes = 0)
    noException should be thrownBy streamingMetrics.recordDiscard(backend = null)

    val keyValueMetrics = Metrics.KeyValueBackend.NoOp
    noException should be thrownBy keyValueMetrics.recordInit(backend = null)
    noException should be thrownBy keyValueMetrics.recordDrop(backend = null)
    noException should be thrownBy keyValueMetrics.recordPut(backend = null)
    noException should be thrownBy keyValueMetrics.recordGet(backend = null)
    noException should be thrownBy keyValueMetrics.recordGet(backend = null, entries = 0)
    noException should be thrownBy keyValueMetrics.recordDelete(backend = null)

    val eventLogMetrics = Metrics.EventLogBackend.NoOp
    noException should be thrownBy eventLogMetrics.recordEvent(backend = null)
    noException should be thrownBy eventLogMetrics.recordEventFailure(backend = null)

    val manifestMetrics = Metrics.ManifestStore.NoOp
    noException should be thrownBy manifestMetrics.recordManifest(manifest = null)

    val reservationMetrics = Metrics.ReservationStore.NoOp
    noException should be thrownBy reservationMetrics.recordReservation(reservation = null)
  }

  they should "provide a default implementation" in {
    val meter = MockMeter()

    val streamingMetrics = new Metrics.StreamingBackend.Default(meter = meter, namespace = "test")
    streamingMetrics.recordInit(backend = "test")
    streamingMetrics.recordDrop(backend = "test")
    streamingMetrics.recordWrite(backend = "test", bytes = 1)
    streamingMetrics.recordWrite(backend = "test", bytes = 2)
    streamingMetrics.recordWrite(backend = "test", bytes = 3)
    streamingMetrics.recordRead(backend = "test", bytes = 4)
    streamingMetrics.recordDiscard(backend = "test")
    streamingMetrics.recordDiscard(backend = "test")

    meter.metric(name = "test_persistence_streaming_init_operations") should be(1)
    meter.metric(name = "test_persistence_streaming_drop_operations") should be(1)
    meter.metric(name = "test_persistence_streaming_write_operations") should be(3)
    meter.metric(name = "test_persistence_streaming_write_bytes") should be(3)
    meter.metric(name = "test_persistence_streaming_read_operations") should be(1)
    meter.metric(name = "test_persistence_streaming_read_bytes") should be(1)
    meter.metric(name = "test_persistence_streaming_discard_operations") should be(2)

    val keyValueMetrics = new Metrics.KeyValueBackend.Default(meter = meter, namespace = "test")
    keyValueMetrics.recordInit(backend = "test")
    keyValueMetrics.recordDrop(backend = "test")
    keyValueMetrics.recordPut(backend = "test")
    keyValueMetrics.recordPut(backend = "test")
    keyValueMetrics.recordPut(backend = "test")
    keyValueMetrics.recordGet(backend = "test")
    keyValueMetrics.recordGet(backend = "test", entries = 4)
    keyValueMetrics.recordDelete(backend = "test")
    keyValueMetrics.recordDelete(backend = "test")

    meter.metric(name = "test_persistence_kv_init_operations") should be(1)
    meter.metric(name = "test_persistence_kv_drop_operations") should be(1)
    meter.metric(name = "test_persistence_kv_put_operations") should be(3)
    meter.metric(name = "test_persistence_kv_get_operations") should be(2)
    meter.metric(name = "test_persistence_kv_delete_operations") should be(2)

    val eventLogMetrics = new Metrics.EventLogBackend.Default(meter = meter, namespace = "test")
    eventLogMetrics.recordEvent(backend = "test")
    eventLogMetrics.recordEvent(backend = "test")
    eventLogMetrics.recordEventFailure(backend = "test")

    meter.metric(name = "test_persistence_event_log_events") should be(2)
    meter.metric(name = "test_persistence_event_log_event_failures") should be(1)

    val manifestMetrics = new Metrics.ManifestStore.Default(meter = meter, namespace = "test")
    manifestMetrics.recordManifest(manifest = Generators.generateManifest.copy(destinations = Seq(Node.generateId())))

    meter.metric(name = "test_persistence_manifest_store_manifests") should be(1)

    val reservationMetrics = new Metrics.ReservationStore.Default(meter = meter, namespace = "test")
    reservationMetrics.recordReservation(reservation = Generators.generateReservation)
    reservationMetrics.recordReservation(reservation = Generators.generateReservation)

    meter.metric(name = "test_persistence_reservation_store_reservations") should be(2)
  }
}
