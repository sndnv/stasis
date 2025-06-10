package stasis.layers.telemetry.analytics

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.Future

import org.apache.pekko.Done

class MockAnalyticsClient(result: Future[Done]) extends AnalyticsClient {
  import scala.concurrent.ExecutionContext.Implicits.global

  private val sentRef: AtomicInteger = new AtomicInteger(0)

  private val lastEntryRef: AtomicReference[Option[AnalyticsEntry]] = new AtomicReference(None)

  override def sendAnalyticsEntry(entry: AnalyticsEntry): Future[Done] =
    result
      .map { _ =>
        val _ = sentRef.incrementAndGet()
        lastEntryRef.set(Some(entry))
        Done
      }

  def sent: Int = sentRef.get()

  def lastEntry: Option[AnalyticsEntry] = lastEntryRef.get()
}

object MockAnalyticsClient {
  def apply(): MockAnalyticsClient =
    new MockAnalyticsClient(result = Future.successful(Done))

  def MockAnalyticsClient(result: Future[Done]): MockAnalyticsClient =
    new MockAnalyticsClient(result = result)
}
