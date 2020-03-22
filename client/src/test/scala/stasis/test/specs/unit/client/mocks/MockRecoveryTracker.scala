package stasis.test.specs.unit.client.mocks

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

import stasis.client.tracking.RecoveryTracker
import stasis.shared.ops.Operation
import stasis.test.specs.unit.client.mocks.MockRecoveryTracker.Statistic

class MockRecoveryTracker extends RecoveryTracker {
  private val stats: Map[Statistic, AtomicInteger] = Map(
    Statistic.FileExamined -> new AtomicInteger(0),
    Statistic.FileCollected -> new AtomicInteger(0),
    Statistic.FileProcessed -> new AtomicInteger(0),
    Statistic.MetadataApplied -> new AtomicInteger(0),
    Statistic.FailureEncountered -> new AtomicInteger(0),
    Statistic.Completed -> new AtomicInteger(0)
  )

  override def fileExamined(
    file: Path,
    metadataChanged: Boolean,
    contentChanged: Boolean
  )(implicit operation: Operation.Id): Unit =
    stats(Statistic.FileExamined).incrementAndGet()

  override def fileCollected(file: Path)(implicit operation: Operation.Id): Unit =
    stats(Statistic.FileCollected).incrementAndGet()

  override def fileProcessed(file: Path)(implicit operation: Operation.Id): Unit =
    stats(Statistic.FileProcessed).incrementAndGet()

  override def metadataApplied(file: Path)(implicit operation: Operation.Id): Unit =
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
    case object FileExamined extends Statistic
    case object FileCollected extends Statistic
    case object FileProcessed extends Statistic
    case object MetadataApplied extends Statistic
    case object FailureEncountered extends Statistic
    case object Completed extends Statistic
  }
}
