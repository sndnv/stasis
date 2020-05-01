package stasis.core.persistence.events

import akka.NotUsed
import akka.stream.scaladsl.Source

import scala.concurrent.Future

trait EventLogView[S] {
  def state: Future[S]
  def stateStream: Source[S, NotUsed]
}
