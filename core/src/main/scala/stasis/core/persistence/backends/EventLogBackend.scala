package stasis.core.persistence.backends

import akka.stream.scaladsl.Source
import akka.{Done, NotUsed}

import scala.concurrent.Future

trait EventLogBackend[E, S] {
  def getState: Future[S]
  def getStateStream: Source[S, NotUsed]
  def storeEventAndUpdateState(event: E, update: (E, S) => S): Future[Done]
}
