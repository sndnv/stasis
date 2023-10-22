package stasis.test.specs.unit.client.mocks

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import stasis.client.model.TargetEntity
import stasis.client.tracking.RecoveryTracker
import stasis.client.tracking.state.RecoveryState
import stasis.shared.ops.Operation
import stasis.test.specs.unit.client.mocks.MockRecoveryTracker.Statistic

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.Future

class MockRecoveryTracker extends RecoveryTracker {
  private val stats: Map[Statistic, AtomicInteger] = Map(
    Statistic.EntityExamined -> new AtomicInteger(0),
    Statistic.EntityCollected -> new AtomicInteger(0),
    Statistic.EntityProcessingStarted -> new AtomicInteger(0),
    Statistic.EntityPartProcessed -> new AtomicInteger(0),
    Statistic.EntityProcessed -> new AtomicInteger(0),
    Statistic.MetadataApplied -> new AtomicInteger(0),
    Statistic.FailureEncountered -> new AtomicInteger(0),
    Statistic.Completed -> new AtomicInteger(0)
  )

  override def state: Future[Map[Operation.Id, RecoveryState]] =
    Future.successful(Map.empty)

  override def updates(operation: Operation.Id): Source[RecoveryState, NotUsed] =
    Source.empty

  override def exists(operation: Operation.Id): Future[Boolean] = Future.successful(false)

  override def remove(operation: Operation.Id): Unit = ()

  override def entityExamined(
    entity: Path,
    metadataChanged: Boolean,
    contentChanged: Boolean
  )(implicit operation: Operation.Id): Unit =
    stats(Statistic.EntityExamined).incrementAndGet()

  override def entityCollected(entity: TargetEntity)(implicit operation: Operation.Id): Unit =
    stats(Statistic.EntityCollected).incrementAndGet()

  override def entityProcessingStarted(entity: Path, expectedParts: Int)(implicit operation: Operation.Id): Unit =
    stats(Statistic.EntityProcessingStarted).incrementAndGet()

  override def entityPartProcessed(entity: Path)(implicit operation: Operation.Id): Unit =
    stats(Statistic.EntityPartProcessed).incrementAndGet()

  override def entityProcessed(entity: Path)(implicit operation: Operation.Id): Unit =
    stats(Statistic.EntityProcessed).incrementAndGet()

  override def metadataApplied(entity: Path)(implicit operation: Operation.Id): Unit =
    stats(Statistic.MetadataApplied).incrementAndGet()

  override def failureEncountered(failure: Throwable)(implicit operation: Operation.Id): Unit =
    stats(Statistic.FailureEncountered).incrementAndGet()

  override def failureEncountered(entity: Path, failure: Throwable)(implicit operation: Operation.Id): Unit =
    stats(Statistic.FailureEncountered).incrementAndGet()

  override def completed()(implicit operation: Operation.Id): Unit =
    stats(Statistic.Completed).incrementAndGet()

  def statistics: Map[Statistic, Int] = stats.view.mapValues(_.get()).toMap
}

object MockRecoveryTracker {
  sealed trait Statistic
  object Statistic {
    case object EntityExamined extends Statistic
    case object EntityCollected extends Statistic
    case object EntityProcessingStarted extends Statistic
    case object EntityPartProcessed extends Statistic
    case object EntityProcessed extends Statistic
    case object MetadataApplied extends Statistic
    case object FailureEncountered extends Statistic
    case object Completed extends Statistic
  }
}
