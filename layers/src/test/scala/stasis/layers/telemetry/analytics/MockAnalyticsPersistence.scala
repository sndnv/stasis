package stasis.layers.telemetry.analytics

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

import scala.collection.mutable
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.apache.pekko.Done

class MockAnalyticsPersistence(existing: Try[Option[AnalyticsEntry]]) extends AnalyticsPersistence {
  override type Self = this.type

  private val cachedEntries: mutable.Queue[AnalyticsEntry] = mutable.Queue.empty
  private val transmittedEntries: mutable.Queue[AnalyticsEntry] = mutable.Queue.empty

  private val lastCachedRef: AtomicReference[Instant] = new AtomicReference(Instant.EPOCH)
  private val lastTransmittedRef: AtomicReference[Instant] = new AtomicReference(Instant.EPOCH)

  override def cache(entry: AnalyticsEntry): Unit = {
    lastCachedRef.set(Instant.now())
    cachedEntries.enqueue(entry)
  }

  override def transmit(entry: AnalyticsEntry): Future[Done] = {
    lastTransmittedRef.set(Instant.now())
    transmittedEntries.enqueue(entry)
    Future.successful(Done)
  }

  override def restore(): Future[Option[AnalyticsEntry]] =
    Future.fromTry(existing)

  override def lastCached: Instant = lastCachedRef.get()

  override def lastTransmitted: Instant = lastTransmittedRef.get()

  def cached: Seq[AnalyticsEntry] = cachedEntries.toSeq

  def transmitted: Seq[AnalyticsEntry] = transmittedEntries.toSeq

  override def withClientProvider(provider: AnalyticsClient.Provider): Self =
    this
}

object MockAnalyticsPersistence {
  def apply(): MockAnalyticsPersistence =
    new MockAnalyticsPersistence(existing = Success(None))

  def apply(existing: AnalyticsEntry): MockAnalyticsPersistence =
    new MockAnalyticsPersistence(existing = Success(Some(existing)))

  def apply(existing: Throwable): MockAnalyticsPersistence =
    new MockAnalyticsPersistence(existing = Failure(existing))
}
