package stasis.core.persistence.backends

import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.{Done, NotUsed}

import scala.concurrent.Future

trait EventLogBackend[E, S] {
  def getState: Future[S]
  def getStateStream: Source[S, NotUsed]
  def storeEventAndUpdateState(event: E, update: (E, S) => S): Future[Done]
}
