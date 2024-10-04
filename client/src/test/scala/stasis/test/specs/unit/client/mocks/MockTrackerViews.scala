package stasis.test.specs.unit.client.mocks

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.Future

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source

import stasis.client.tracking.BackupTracker
import stasis.client.tracking.RecoveryTracker
import stasis.client.tracking.ServerTracker
import stasis.client.tracking.TrackerViews
import stasis.client.tracking.state.BackupState
import stasis.client.tracking.state.RecoveryState
import stasis.shared.ops.Operation
import stasis.test.specs.unit.client.mocks.MockTrackerViews.Statistic

class MockTrackerViews extends TrackerViews {
  private val stats: Map[Statistic, AtomicInteger] = Map(
    Statistic.GetState -> new AtomicInteger(0),
    Statistic.GetOperationUpdates -> new AtomicInteger(0)
  )

  override val backup: BackupTracker.View with BackupTracker.Manage = new MockBackupTracker() {
    override def state: Future[Map[Operation.Id, BackupState]] = {
      stats(Statistic.GetState).incrementAndGet()
      super.state
    }

    override def updates(operation: Operation.Id): Source[BackupState, NotUsed] = {
      stats(Statistic.GetOperationUpdates).incrementAndGet()
      super.updates(operation)
    }
  }

  override val recovery: RecoveryTracker.View with RecoveryTracker.Manage = new MockRecoveryTracker() {
    override def state: Future[Map[Operation.Id, RecoveryState]] = {
      stats(Statistic.GetState).incrementAndGet()
      super.state
    }

    override def updates(operation: Operation.Id): Source[RecoveryState, NotUsed] = {
      stats(Statistic.GetOperationUpdates).incrementAndGet()
      super.updates(operation)
    }
  }

  override val server: ServerTracker.View = new MockServerTracker() {
    override def state: Future[Map[String, ServerTracker.ServerState]] = {
      stats(Statistic.GetState).incrementAndGet()
      super.state
    }

    override def updates(server: String): Source[ServerTracker.ServerState, NotUsed] = {
      stats(Statistic.GetOperationUpdates).incrementAndGet()
      super.updates(server)
    }
  }

  def statistics: Map[Statistic, Int] = stats.view.mapValues(_.get()).toMap
}

object MockTrackerViews {
  def apply(): MockTrackerViews = new MockTrackerViews()

  sealed trait Statistic
  object Statistic {
    case object GetState extends Statistic
    case object GetOperationUpdates extends Statistic
  }
}
