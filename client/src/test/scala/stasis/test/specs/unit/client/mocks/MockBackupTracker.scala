package stasis.test.specs.unit.client.mocks

import stasis.client.collection.rules.Rule
import stasis.client.tracking.BackupTracker
import stasis.shared.model.datasets.DatasetEntry
import stasis.shared.ops.Operation
import stasis.shared.ops.Operation.Id
import stasis.test.specs.unit.client.mocks.MockBackupTracker.Statistic

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class MockBackupTracker extends BackupTracker {
  private val stats: Map[Statistic, AtomicInteger] = Map(
    Statistic.EntityDiscovered -> new AtomicInteger(0),
    Statistic.SpecificationProcessed -> new AtomicInteger(0),
    Statistic.EntityExamined -> new AtomicInteger(0),
    Statistic.EntityCollected -> new AtomicInteger(0),
    Statistic.EntityProcessed -> new AtomicInteger(0),
    Statistic.MetadataCollected -> new AtomicInteger(0),
    Statistic.MetadataPushed -> new AtomicInteger(0),
    Statistic.FailureEncountered -> new AtomicInteger(0),
    Statistic.Completed -> new AtomicInteger(0)
  )

  override def entityDiscovered(entity: Path)(implicit operation: Id): Unit =
    stats(Statistic.EntityDiscovered).incrementAndGet()

  override def specificationProcessed(
    unmatched: Seq[(Rule, Throwable)]
  )(implicit operation: Operation.Id): Unit =
    stats(Statistic.SpecificationProcessed).incrementAndGet()

  override def entityExamined(
    entity: Path,
    metadataChanged: Boolean,
    contentChanged: Boolean
  )(implicit operation: Operation.Id): Unit =
    stats(Statistic.EntityExamined).incrementAndGet()

  override def entityCollected(entity: Path)(implicit operation: Operation.Id): Unit =
    stats(Statistic.EntityCollected).incrementAndGet()

  override def entityProcessed(entity: Path, contentChanged: Boolean)(implicit operation: Operation.Id): Unit =
    stats(Statistic.EntityProcessed).incrementAndGet()

  override def metadataCollected()(implicit operation: Operation.Id): Unit =
    stats(Statistic.MetadataCollected).incrementAndGet()

  override def metadataPushed(entry: DatasetEntry.Id)(implicit operation: Operation.Id): Unit =
    stats(Statistic.MetadataPushed).incrementAndGet()

  override def failureEncountered(failure: Throwable)(implicit operation: Operation.Id): Unit =
    stats(Statistic.FailureEncountered).incrementAndGet()

  override def completed()(implicit operation: Operation.Id): Unit =
    stats(Statistic.Completed).incrementAndGet()

  def statistics: Map[Statistic, Int] = stats.view.mapValues(_.get()).toMap
}

object MockBackupTracker {
  sealed trait Statistic
  object Statistic {
    case object EntityDiscovered extends Statistic
    case object SpecificationProcessed extends Statistic
    case object EntityExamined extends Statistic
    case object EntityCollected extends Statistic
    case object EntityProcessed extends Statistic
    case object MetadataCollected extends Statistic
    case object MetadataPushed extends Statistic
    case object FailureEncountered extends Statistic
    case object Completed extends Statistic
  }
}
