package stasis.core.persistence.events

import scala.concurrent.Future

trait EventLogView[S] {
  def state: Future[S]
}
