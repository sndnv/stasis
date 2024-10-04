package stasis.core.persistence.events

import scala.concurrent.Future

import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.Done
import org.apache.pekko.NotUsed

import stasis.core.persistence.backends.EventLogBackend

trait EventLog[E, S] { log =>
  def store(event: E): Future[Done]
  def state: Future[S]
  def stateStream: Source[S, NotUsed]

  def view: EventLogView[S] =
    new EventLogView[S] {
      override def state: Future[S] = log.state
      override def stateStream: Source[S, NotUsed] = log.stateStream
    }
}

object EventLog {
  def apply[E, S](
    backend: EventLogBackend[E, S],
    updateState: (E, S) => S
  ): EventLog[E, S] =
    new EventLog[E, S] {
      override def store(event: E): Future[Done] = backend.storeEventAndUpdateState(event, updateState)
      override def state: Future[S] = backend.getState
      override def stateStream: Source[S, NotUsed] = backend.getStateStream
    }
}
