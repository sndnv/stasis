package stasis.layers.telemetry.mocks

import java.util.concurrent.atomic.AtomicInteger

import stasis.layers.persistence.Metrics

object MockPersistenceMetrics {
  class KeyValueStore extends Metrics.Store {
    private val putRecorded: AtomicInteger = new AtomicInteger(0)
    private val getRecorded: AtomicInteger = new AtomicInteger(0)
    private val deleteRecorded: AtomicInteger = new AtomicInteger(0)

    def put: Int = putRecorded.get()
    def get: Int = getRecorded.get()
    def delete: Int = deleteRecorded.get()

    override def recordPut(store: String): Unit = {
      val _ = putRecorded.incrementAndGet()
    }

    override def recordGet(store: String): Unit = {
      val _ = getRecorded.incrementAndGet()
    }

    override def recordGet(store: String, entries: Int): Unit = {
      val _ = getRecorded.accumulateAndGet(entries, { case (a, b) => a + b })
    }

    override def recordDelete(store: String): Unit = {
      val _ = deleteRecorded.incrementAndGet()
    }
  }

  object KeyValueStore {
    def apply(): KeyValueStore = new KeyValueStore()
  }
}
