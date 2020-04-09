package stasis.test.specs.unit.client.mocks

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

import stasis.client.tracking.RecoveryTracker
import stasis.shared.ops.Operation
import stasis.test.specs.unit.client.mocks.MockRecoveryTracker.Statistic

class MockRecoveryTracker extends RecoveryTracker {
  private val stats: Map[Statistic, AtomicInteger] = Map(
    Statistic.EntityExamined -> new AtomicInteger(0),
    Statistic.EntityCollected -> new AtomicInteger(0),
    Statistic.EntityProcessed -> new AtomicInteger(0),
    Statistic.MetadataApplied -> new AtomicInteger(0),
    Statistic.FailureEncountered -> new AtomicInteger(0),
    Statistic.Completed -> new AtomicInteger(0)
  )

  override def entityExamined(
    entity: Path,
    metadataChanged: Boolean,
    contentChanged: Boolean
  )(implicit operation: Operation.Id): Unit =
    stats(Statistic.EntityExamined).incrementAndGet()

  override def entityCollected(entity: Path)(implicit operation: Operation.Id): Unit =
    stats(Statistic.EntityCollected).incrementAndGet()

  override def entityProcessed(entity: Path)(implicit operation: Operation.Id): Unit =
    stats(Statistic.EntityProcessed).incrementAndGet()

  override def metadataApplied(entity: Path)(implicit operation: Operation.Id): Unit =
    stats(Statistic.MetadataApplied).incrementAndGet()

  override def failureEncountered(failure: Throwable)(implicit operation: Operation.Id): Unit =
    stats(Statistic.FailureEncountered).incrementAndGet()

  override def completed()(implicit operation: Operation.Id): Unit =
    stats(Statistic.Completed).incrementAndGet()

  def statistics: Map[Statistic, Int] = stats.mapValues(_.get())
}

object MockRecoveryTracker {
  sealed trait Statistic
  object Statistic {
    case object EntityExamined extends Statistic
    case object EntityCollected extends Statistic
    case object EntityProcessed extends Statistic
    case object MetadataApplied extends Statistic
    case object FailureEncountered extends Statistic
    case object Completed extends Statistic
  }
}
