package stasis.core.persistence.events

import scala.concurrent.Future

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source

trait EventLogView[S] {
  def state: Future[S]
  def stateStream: Source[S, NotUsed]
}
