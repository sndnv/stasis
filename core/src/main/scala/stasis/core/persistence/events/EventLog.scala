package stasis.core.persistence.events

import scala.concurrent.Future

import akka.Done
import stasis.core.persistence.backends.EventLogBackend

trait EventLog[E, S] { log =>
  def store(event: E): Future[Done]
  def state: Future[S]

  def view: EventLogView[S] = new EventLogView[S] {
    override def state: Future[S] = log.state
  }
}

object EventLog {
  def apply[E, S](
    backend: EventLogBackend[E, S],
    updateState: (E, S) => S
  ): EventLog[E, S] = new EventLog[E, S] {

    override def store(event: E): Future[Done] = backend.storeEventAndUpdateState(event, updateState)
    override def state: Future[S] = backend.getState
  }
}
