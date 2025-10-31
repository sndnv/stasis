package stasis.server.events.mocks

import scala.collection.mutable

import io.github.sndnv.layers.events.Event
import io.github.sndnv.layers.events.EventCollector
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source

class MockEventCollector() extends EventCollector {
  private val collectedEvents: mutable.Queue[Event] = mutable.Queue.empty

  override def publish(event: => Event): Unit = collectedEvents.append(event)

  override def unsubscribe(subscriber: AnyRef): Unit = ()

  override protected def subscribe(subscriber: AnyRef, filter: Option[Event => Boolean]): Source[Event, NotUsed] =
    Source.empty

  def events: Seq[Event] = collectedEvents.toSeq
}

object MockEventCollector {
  def apply(): MockEventCollector =
    new MockEventCollector()
}
