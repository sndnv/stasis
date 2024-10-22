package stasis.test.specs.unit.core.persistence

import java.util.concurrent.atomic.AtomicInteger

import stasis.core.packaging.Manifest
import stasis.core.persistence.CrateStorageReservation
import stasis.core.persistence.Metrics

object MockPersistenceMetrics {
  class StreamingBackend extends Metrics.StreamingBackend {
    private val writeRecorded: AtomicInteger = new AtomicInteger(0)
    private val readRecorded: AtomicInteger = new AtomicInteger(0)
    private val discardRecorded: AtomicInteger = new AtomicInteger(0)

    def write: Int = writeRecorded.get()

    def read: Int = readRecorded.get()

    def discard: Int = discardRecorded.get()

    override def recordWrite(backend: String, bytes: Long): Unit = {
      val _ = writeRecorded.incrementAndGet()
    }

    override def recordRead(backend: String, bytes: Long): Unit = {
      val _ = readRecorded.incrementAndGet()
    }

    override def recordDiscard(backend: String): Unit = {
      val _ = discardRecorded.incrementAndGet()
    }
  }

  object StreamingBackend {
    def apply(): StreamingBackend = new StreamingBackend()
  }

  class EventLogBackend extends Metrics.EventLogBackend {
    private val eventRecorded: AtomicInteger = new AtomicInteger(0)
    private val eventFailureRecorded: AtomicInteger = new AtomicInteger(0)

    def event: Int = eventRecorded.get()

    def eventFailure: Int = eventFailureRecorded.get()

    override def recordEvent(backend: String): Unit = {
      val _ = eventRecorded.incrementAndGet()
    }

    override def recordEventFailure(backend: String): Unit = {
      val _ = eventFailureRecorded.incrementAndGet()
    }
  }

  object EventLogBackend {
    def apply(): EventLogBackend = new EventLogBackend()
  }

  class ManifestStore extends Metrics.ManifestStore {
    private val manifestRecorded: AtomicInteger = new AtomicInteger(0)

    def manifest: Int = manifestRecorded.get()

    override def recordManifest(manifest: Manifest): Unit = {
      val _ = manifestRecorded.incrementAndGet()
    }
  }

  object ManifestStore {
    def apply(): ManifestStore = new ManifestStore()
  }

  class ReservationStore extends Metrics.ReservationStore {
    private val reservationRecorded: AtomicInteger = new AtomicInteger(0)

    def reservation: Int = reservationRecorded.get()

    override def recordReservation(reservation: CrateStorageReservation): Unit = {
      val _ = reservationRecorded.incrementAndGet()
    }
  }

  object ReservationStore {
    def apply(): ReservationStore = new ReservationStore()
  }
}
