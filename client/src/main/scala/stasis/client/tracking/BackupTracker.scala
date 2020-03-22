package stasis.client.tracking

import java.nio.file.Path

import stasis.shared.model.datasets.DatasetEntry
import stasis.shared.ops.Operation

trait BackupTracker {
  def fileExamined(file: Path, metadataChanged: Boolean, contentChanged: Boolean)(implicit operation: Operation.Id): Unit
  def fileCollected(file: Path)(implicit operation: Operation.Id): Unit
  def fileProcessed(file: Path, contentChanged: Boolean)(implicit operation: Operation.Id): Unit
  def metadataCollected()(implicit operation: Operation.Id): Unit
  def metadataPushed(entry: DatasetEntry.Id)(implicit operation: Operation.Id): Unit
  def failureEncountered(failure: Throwable)(implicit operation: Operation.Id): Unit
  def completed()(implicit operation: Operation.Id): Unit
}
