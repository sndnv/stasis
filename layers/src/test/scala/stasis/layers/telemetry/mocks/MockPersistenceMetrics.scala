package stasis.layers.telemetry.mocks

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.Future

import stasis.layers.persistence.Metrics
import stasis.layers.persistence.Metrics.Store

object MockPersistenceMetrics {
  class KeyValueStore extends Metrics.Store {
    private val putRecorded: AtomicInteger = new AtomicInteger(0)
    private val getRecorded: AtomicInteger = new AtomicInteger(0)
    private val deleteRecorded: AtomicInteger = new AtomicInteger(0)
    private val containsRecorded: AtomicInteger = new AtomicInteger(0)
    private val listRecorded: AtomicInteger = new AtomicInteger(0)

    def put: Int = putRecorded.get()
    def get: Int = getRecorded.get()
    def delete: Int = deleteRecorded.get()
    def contains: Int = containsRecorded.get()
    def list: Int = listRecorded.get()

    override def recordPut[T](store: String)(f: => Future[T]): Future[T] = {
      val _ = putRecorded.incrementAndGet()
      f
    }

    override def recordGet[T](store: String)(f: => Future[T]): Future[T] = {
      val _ = getRecorded.incrementAndGet()
      f
    }

    override def recordDelete[T](store: String)(f: => Future[T]): Future[T] = {
      val _ = deleteRecorded.incrementAndGet()
      f
    }

    override def recordContains[T](store: String)(f: => Future[T]): Future[T] = {
      val _ = containsRecorded.incrementAndGet()
      f
    }

    override def recordList[T](store: String)(f: => Future[T]): Future[T] = {
      val _ = listRecorded.incrementAndGet()
      f
    }

    override def recordOperation[T](store: String, operation: Store.Operation)(f: => Future[T]): Future[T] = f
  }

  object KeyValueStore {
    def apply(): KeyValueStore = new KeyValueStore()
  }
}
