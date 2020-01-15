package stasis.test.specs.unit.client.mocks

import java.util.concurrent.atomic.AtomicInteger

import stasis.client.tracking.TrackerView
import stasis.test.specs.unit.client.mocks.MockTrackerView.Statistic

import scala.concurrent.Future

class MockTrackerView extends TrackerView {
  private val stats: Map[Statistic, AtomicInteger] = Map(
    Statistic.GetState -> new AtomicInteger(0)
  )
  override def state: Future[TrackerView.State] = {
    stats(Statistic.GetState).incrementAndGet()
    Future.successful(
      TrackerView.State(
        operations = Map.empty,
        servers = Map.empty
      )
    )
  }

  def statistics: Map[Statistic, Int] = stats.mapValues(_.get())
}

object MockTrackerView {
  def apply(): MockTrackerView = new MockTrackerView()

  sealed trait Statistic
  object Statistic {
    case object GetState extends Statistic
  }
}
