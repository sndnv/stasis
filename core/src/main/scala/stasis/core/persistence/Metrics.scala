package stasis.core.persistence

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.metrics.Meter

import stasis.core.packaging.Manifest
import stasis.layers.telemetry.metrics.MeterExtensions._
import stasis.layers.telemetry.metrics.MetricsProvider

object Metrics {
  def noop(): Set[MetricsProvider] = Set(
    StreamingBackend.NoOp,
    EventLogBackend.NoOp,
    ManifestStore.NoOp,
    ReservationStore.NoOp
  )

  def default(meter: Meter, namespace: String): Set[MetricsProvider] = Set(
    new StreamingBackend.Default(meter, namespace),
    new EventLogBackend.Default(meter, namespace),
    new ManifestStore.Default(meter, namespace),
    new ReservationStore.Default(meter, namespace)
  )

  trait StreamingBackend extends MetricsProvider {
    def recordInit(backend: String): Unit
    def recordDrop(backend: String): Unit
    def recordWrite(backend: String, bytes: Long): Unit
    def recordRead(backend: String, bytes: Long): Unit
    def recordDiscard(backend: String): Unit
  }

  object StreamingBackend {
    object NoOp extends StreamingBackend {
      override def recordInit(backend: String): Unit = ()
      override def recordDrop(backend: String): Unit = ()
      override def recordWrite(backend: String, bytes: Long): Unit = ()
      override def recordRead(backend: String, bytes: Long): Unit = ()
      override def recordDiscard(backend: String): Unit = ()
    }

    class Default(meter: Meter, namespace: String) extends StreamingBackend {
      private val subsystem: String = "persistence_streaming"

      private val initOperations = meter.counter(name = s"${namespace}_${subsystem}_init_operations")
      private val dropOperations = meter.counter(name = s"${namespace}_${subsystem}_drop_operations")
      private val writeOperations = meter.counter(name = s"${namespace}_${subsystem}_write_operations")
      private val writeBytes = meter.counter(name = s"${namespace}_${subsystem}_write_bytes")
      private val readOperations = meter.counter(name = s"${namespace}_${subsystem}_read_operations")
      private val readBytes = meter.counter(name = s"${namespace}_${subsystem}_read_bytes")
      private val discardOperations = meter.counter(name = s"${namespace}_${subsystem}_discard_operations")

      override def recordInit(backend: String): Unit =
        initOperations.inc(Labels.Backend -> backend)

      override def recordDrop(backend: String): Unit =
        dropOperations.inc(Labels.Backend -> backend)

      override def recordWrite(backend: String, bytes: Long): Unit = {
        writeOperations.inc(Labels.Backend -> backend)
        writeBytes.add(value = bytes, Labels.Backend -> backend)
      }

      override def recordRead(backend: String, bytes: Long): Unit = {
        readOperations.inc(Labels.Backend -> backend)
        readBytes.add(value = bytes, Labels.Backend -> backend)
      }

      override def recordDiscard(backend: String): Unit =
        discardOperations.inc(Labels.Backend -> backend)
    }
  }

  trait EventLogBackend extends MetricsProvider {
    def recordEvent(backend: String): Unit
    def recordEventFailure(backend: String): Unit
  }

  object EventLogBackend {
    object NoOp extends EventLogBackend {
      override def recordEvent(backend: String): Unit = ()
      override def recordEventFailure(backend: String): Unit = ()
    }

    class Default(meter: Meter, namespace: String) extends EventLogBackend {
      private val subsystem: String = "persistence_event_log"

      private val events = meter.counter(name = s"${namespace}_${subsystem}_events")
      private val eventFailures = meter.counter(name = s"${namespace}_${subsystem}_event_failures")

      override def recordEvent(backend: String): Unit =
        events.inc(Labels.Backend -> backend)

      override def recordEventFailure(backend: String): Unit =
        eventFailures.inc(Labels.Backend -> backend)
    }
  }

  trait ManifestStore extends MetricsProvider {
    def recordManifest(manifest: Manifest): Unit
  }

  object ManifestStore {
    object NoOp extends ManifestStore {
      override def recordManifest(manifest: Manifest): Unit = ()
    }

    class Default(meter: Meter, namespace: String) extends ManifestStore {
      private val subsystem: String = "persistence_manifest_store"

      private val manifests = meter.counter(name = s"${namespace}_${subsystem}_manifests")
      private val manifestBytes = meter.counter(name = s"${namespace}_${subsystem}_manifest_bytes")

      override def recordManifest(manifest: Manifest): Unit = {
        manifests.inc()
        manifestBytes.add(value = manifest.size)
      }
    }
  }

  trait ReservationStore extends MetricsProvider {
    def recordReservation(reservation: CrateStorageReservation): Unit
  }

  object ReservationStore {
    object NoOp extends ReservationStore {
      override def recordReservation(reservation: CrateStorageReservation): Unit = ()
    }

    class Default(meter: Meter, namespace: String) extends ReservationStore {
      private val subsystem: String = "persistence_reservation_store"

      private val reservations = meter.counter(name = s"${namespace}_${subsystem}_reservations")
      private val reservationBytes = meter.counter(name = s"${namespace}_${subsystem}_reservation_bytes")

      override def recordReservation(reservation: CrateStorageReservation): Unit = {
        reservations.inc(Labels.Target -> reservation.target.toString)
        reservationBytes.add(value = reservation.size, Labels.Target -> reservation.target.toString)
      }
    }
  }

  object Labels {
    val Backend: AttributeKey[String] = AttributeKey.stringKey("backend")
    val Target: AttributeKey[String] = AttributeKey.stringKey("target")
  }
}
