package stasis.core.persistence.backends

import scala.collection.immutable.Queue
import scala.concurrent.Future

import akka.Done

trait EventLogBackend[E, S] {
  def getState: Future[S]
  def getEvents: Future[Queue[E]]
  def storeEventAndUpdateState(event: E, update: (E, S) => S): Future[Done]
}
