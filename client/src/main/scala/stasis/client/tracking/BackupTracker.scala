package stasis.client.tracking

import akka.NotUsed
import akka.stream.scaladsl.Source
import stasis.client.collection.rules.Rule
import stasis.client.model.{EntityMetadata, SourceEntity}
import stasis.client.tracking.state.BackupState
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.shared.ops.Operation

import java.nio.file.Path
import scala.concurrent.Future

trait BackupTracker extends BackupTracker.View with BackupTracker.Manage {
  def started(definition: DatasetDefinition.Id)(implicit operation: Operation.Id): Unit
  def entityDiscovered(entity: Path)(implicit operation: Operation.Id): Unit
  def specificationProcessed(unmatched: Seq[(Rule, Throwable)])(implicit operation: Operation.Id): Unit
  def entityExamined(entity: Path, metadataChanged: Boolean, contentChanged: Boolean)(implicit operation: Operation.Id): Unit
  def entityCollected(entity: SourceEntity)(implicit operation: Operation.Id): Unit
  def entityProcessingStarted(entity: Path, expectedParts: Int)(implicit operation: Operation.Id): Unit
  def entityPartProcessed(entity: Path)(implicit operation: Operation.Id): Unit
  def entityProcessed(entity: Path, metadata: Either[EntityMetadata, EntityMetadata])(implicit operation: Operation.Id): Unit
  def metadataCollected()(implicit operation: Operation.Id): Unit
  def metadataPushed(entry: DatasetEntry.Id)(implicit operation: Operation.Id): Unit
  def failureEncountered(failure: Throwable)(implicit operation: Operation.Id): Unit
  def failureEncountered(entity: Path, failure: Throwable)(implicit operation: Operation.Id): Unit
  def completed()(implicit operation: Operation.Id): Unit
}

object BackupTracker {
  trait View {
    def state: Future[Map[Operation.Id, BackupState]]
    def updates(operation: Operation.Id): Source[BackupState, NotUsed]
  }

  trait Manage {
    def remove(operation: Operation.Id): Unit
  }
}
