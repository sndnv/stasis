package stasis.test.specs.unit.core.telemetry.mocks

import stasis.core.packaging
import stasis.core.persistence.{CrateStorageReservation, Metrics}

import java.util.concurrent.atomic.AtomicInteger

object MockPersistenceMetrics {
  class StreamingBackend extends Metrics.StreamingBackend {
    private val initRecorded: AtomicInteger = new AtomicInteger(0)
    private val dropRecorded: AtomicInteger = new AtomicInteger(0)
    private val writeRecorded: AtomicInteger = new AtomicInteger(0)
    private val readRecorded: AtomicInteger = new AtomicInteger(0)
    private val discardRecorded: AtomicInteger = new AtomicInteger(0)

    def init: Int = initRecorded.get()
    def drop: Int = dropRecorded.get()
    def write: Int = writeRecorded.get()
    def read: Int = readRecorded.get()
    def discard: Int = discardRecorded.get()

    override def recordInit(backend: String): Unit = {
      val _ = initRecorded.incrementAndGet()
    }

    override def recordDrop(backend: String): Unit = {
      val _ = dropRecorded.incrementAndGet()
    }

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

  class KeyValueBackend extends Metrics.KeyValueBackend {
    private val initRecorded: AtomicInteger = new AtomicInteger(0)
    private val dropRecorded: AtomicInteger = new AtomicInteger(0)
    private val putRecorded: AtomicInteger = new AtomicInteger(0)
    private val getRecorded: AtomicInteger = new AtomicInteger(0)
    private val deleteRecorded: AtomicInteger = new AtomicInteger(0)

    def init: Int = initRecorded.get()
    def drop: Int = dropRecorded.get()
    def put: Int = putRecorded.get()
    def get: Int = getRecorded.get()
    def delete: Int = deleteRecorded.get()

    override def recordInit(backend: String): Unit = {
      val _ = initRecorded.incrementAndGet()
    }

    override def recordDrop(backend: String): Unit = {
      val _ = dropRecorded.incrementAndGet()
    }

    override def recordPut(backend: String): Unit = {
      val _ = putRecorded.incrementAndGet()
    }

    override def recordGet(backend: String): Unit = {
      val _ = getRecorded.incrementAndGet()
    }

    override def recordGet(backend: String, entries: Int): Unit = {
      val _ = getRecorded.accumulateAndGet(entries, { case (a, b) => a + b })
    }

    override def recordDelete(backend: String): Unit = {
      val _ = deleteRecorded.incrementAndGet()
    }
  }

  object KeyValueBackend {
    def apply(): KeyValueBackend = new KeyValueBackend()
  }

  class EventLogBackend extends Metrics.EventLogBackend {
    private val eventRecorded: AtomicInteger = new AtomicInteger(0)

    def event: Int = eventRecorded.get()

    override def recordEvent(backend: String): Unit = {
      val _ = eventRecorded.incrementAndGet()
    }
  }

  object EventLogBackend {
    def apply(): EventLogBackend = new EventLogBackend()
  }

  class ManifestStore extends Metrics.ManifestStore {
    private val manifestRecorded: AtomicInteger = new AtomicInteger(0)

    def manifest: Int = manifestRecorded.get()

    override def recordManifest(manifest: packaging.Manifest): Unit = {
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
