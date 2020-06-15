package stasis.test.specs.unit.client.mocks

import java.util.concurrent.atomic.AtomicInteger

import akka.NotUsed
import akka.stream.scaladsl.Source
import stasis.client.tracking.TrackerView
import stasis.shared.ops.Operation
import stasis.shared.ops.Operation.Id
import stasis.test.specs.unit.client.mocks.MockTrackerView.Statistic

import scala.concurrent.Future

class MockTrackerView extends TrackerView {
  private val stats: Map[Statistic, AtomicInteger] = Map(
    Statistic.GetState -> new AtomicInteger(0),
    Statistic.GetStateUpdates -> new AtomicInteger(0),
    Statistic.GetOperationUpdates -> new AtomicInteger(0)
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

  override def stateUpdates: Source[TrackerView.State, NotUsed] = {
    stats(Statistic.GetStateUpdates).incrementAndGet()
    Source.empty
  }

  override def operationUpdates(operation: Id): Source[Operation.Progress, NotUsed] = {
    stats(Statistic.GetOperationUpdates).incrementAndGet()
    Source.empty
  }

  def statistics: Map[Statistic, Int] = stats.view.mapValues(_.get()).toMap
}

object MockTrackerView {
  def apply(): MockTrackerView = new MockTrackerView()

  sealed trait Statistic
  object Statistic {
    case object GetState extends Statistic
    case object GetStateUpdates extends Statistic
    case object GetOperationUpdates extends Statistic
  }
}
