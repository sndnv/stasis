package stasis.core.persistence.backends

import scala.concurrent.Future

import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.Done
import org.apache.pekko.NotUsed

trait EventLogBackend[E, S] {
  def getState: Future[S]
  def getStateStream: Source[S, NotUsed]
  def storeEventAndUpdateState(event: E, update: (E, S) => S): Future[Done]
}
