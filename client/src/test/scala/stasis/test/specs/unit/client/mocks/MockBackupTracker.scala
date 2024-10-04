package stasis.test.specs.unit.client.mocks

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.Future

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source

import stasis.client.collection.rules.Rule
import stasis.client.model.EntityMetadata
import stasis.client.model.SourceEntity
import stasis.client.tracking.BackupTracker
import stasis.client.tracking.state.BackupState
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.datasets.DatasetEntry
import stasis.shared.ops.Operation
import stasis.test.specs.unit.client.mocks.MockBackupTracker.Statistic

class MockBackupTracker extends BackupTracker {
  private val stats: Map[Statistic, AtomicInteger] = Map(
    Statistic.Started -> new AtomicInteger(0),
    Statistic.EntityDiscovered -> new AtomicInteger(0),
    Statistic.SpecificationProcessed -> new AtomicInteger(0),
    Statistic.EntityExamined -> new AtomicInteger(0),
    Statistic.EntitySkipped -> new AtomicInteger(0),
    Statistic.EntityCollected -> new AtomicInteger(0),
    Statistic.EntityProcessingStarted -> new AtomicInteger(0),
    Statistic.EntityPartProcessed -> new AtomicInteger(0),
    Statistic.EntityProcessed -> new AtomicInteger(0),
    Statistic.MetadataCollected -> new AtomicInteger(0),
    Statistic.MetadataPushed -> new AtomicInteger(0),
    Statistic.FailureEncountered -> new AtomicInteger(0),
    Statistic.Completed -> new AtomicInteger(0)
  )

  override def state: Future[Map[Operation.Id, BackupState]] =
    Future.successful(Map.empty)

  override def updates(operation: Operation.Id): Source[BackupState, NotUsed] =
    Source.empty

  override def exists(operation: Operation.Id): Future[Boolean] = Future.successful(false)

  override def remove(operation: Operation.Id): Unit = ()

  override def started(definition: DatasetDefinition.Id)(implicit operation: Operation.Id): Unit =
    stats(Statistic.Started).incrementAndGet()

  override def entityDiscovered(entity: Path)(implicit operation: Operation.Id): Unit =
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

  override def entitySkipped(entity: Path)(implicit operation: Operation.Id): Unit =
    stats(Statistic.EntitySkipped).incrementAndGet()

  override def entityCollected(entity: SourceEntity)(implicit operation: Operation.Id): Unit =
    stats(Statistic.EntityCollected).incrementAndGet()

  override def entityProcessingStarted(entity: Path, expectedParts: Int)(implicit operation: Operation.Id): Unit =
    stats(Statistic.EntityProcessingStarted).incrementAndGet()

  override def entityPartProcessed(entity: Path)(implicit operation: Operation.Id): Unit =
    stats(Statistic.EntityPartProcessed).incrementAndGet()

  override def entityProcessed(entity: Path, metadata: Either[EntityMetadata, EntityMetadata])(implicit
    operation: Operation.Id
  ): Unit =
    stats(Statistic.EntityProcessed).incrementAndGet()

  override def metadataCollected()(implicit operation: Operation.Id): Unit =
    stats(Statistic.MetadataCollected).incrementAndGet()

  override def metadataPushed(entry: DatasetEntry.Id)(implicit operation: Operation.Id): Unit =
    stats(Statistic.MetadataPushed).incrementAndGet()

  override def failureEncountered(failure: Throwable)(implicit operation: Operation.Id): Unit =
    stats(Statistic.FailureEncountered).incrementAndGet()

  override def failureEncountered(entity: Path, failure: Throwable)(implicit operation: Operation.Id): Unit =
    stats(Statistic.FailureEncountered).incrementAndGet()

  override def completed()(implicit operation: Operation.Id): Unit =
    stats(Statistic.Completed).incrementAndGet()

  def statistics: Map[Statistic, Int] = stats.view.mapValues(_.get()).toMap
}

object MockBackupTracker {
  def apply(): MockBackupTracker = new MockBackupTracker()

  sealed trait Statistic
  object Statistic {
    case object Started extends Statistic
    case object EntityDiscovered extends Statistic
    case object SpecificationProcessed extends Statistic
    case object EntityExamined extends Statistic
    case object EntitySkipped extends Statistic
    case object EntityCollected extends Statistic
    case object EntityProcessingStarted extends Statistic
    case object EntityPartProcessed extends Statistic
    case object EntityProcessed extends Statistic
    case object MetadataCollected extends Statistic
    case object MetadataPushed extends Statistic
    case object FailureEncountered extends Statistic
    case object Completed extends Statistic
  }
}
