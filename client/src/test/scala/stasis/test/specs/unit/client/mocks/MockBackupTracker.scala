package stasis.test.specs.unit.client.mocks

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

import stasis.client.tracking.BackupTracker
import stasis.shared.ops.Operation
import stasis.test.specs.unit.client.mocks.MockBackupTracker.Statistic

class MockBackupTracker extends BackupTracker {
  private val stats: Map[Statistic, AtomicInteger] = Map(
    Statistic.FileCollected -> new AtomicInteger(0),
    Statistic.FileProcessed -> new AtomicInteger(0),
    Statistic.MetadataCollected -> new AtomicInteger(0),
    Statistic.MetadataPushed -> new AtomicInteger(0),
    Statistic.FailureEncountered -> new AtomicInteger(0),
    Statistic.Completed -> new AtomicInteger(0)
  )

  override def fileCollected(file: Path)(implicit operation: Operation.Id): Unit =
    stats(Statistic.FileCollected).incrementAndGet()

  override def fileProcessed(file: Path)(implicit operation: Operation.Id): Unit =
    stats(Statistic.FileProcessed).incrementAndGet()

  override def metadataCollected()(implicit operation: Operation.Id): Unit =
    stats(Statistic.MetadataCollected).incrementAndGet()

  override def metadataPushed()(implicit operation: Operation.Id): Unit =
    stats(Statistic.MetadataPushed).incrementAndGet()

  override def failureEncountered(failure: Throwable)(implicit operation: Operation.Id): Unit =
    stats(Statistic.FailureEncountered).incrementAndGet()

  override def completed()(implicit operation: Operation.Id): Unit =
    stats(Statistic.Completed).incrementAndGet()

  def statistics: Map[Statistic, Int] = stats.mapValues(_.get())
}

object MockBackupTracker {
  sealed trait Statistic
  object Statistic {
    case object FileCollected extends Statistic
    case object FileProcessed extends Statistic
    case object MetadataCollected extends Statistic
    case object MetadataPushed extends Statistic
    case object FailureEncountered extends Statistic
    case object Completed extends Statistic
  }
}
