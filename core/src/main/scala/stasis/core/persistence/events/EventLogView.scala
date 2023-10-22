package stasis.core.persistence.events

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source

import scala.concurrent.Future

trait EventLogView[S] {
  def state: Future[S]
  def stateStream: Source[S, NotUsed]
}
