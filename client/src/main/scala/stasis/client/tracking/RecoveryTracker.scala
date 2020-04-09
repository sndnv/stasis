package stasis.client.tracking

import java.nio.file.Path

import stasis.shared.ops.Operation

trait RecoveryTracker {
  def entityExamined(entity: Path, metadataChanged: Boolean, contentChanged: Boolean)(implicit operation: Operation.Id): Unit
  def entityCollected(entity: Path)(implicit operation: Operation.Id): Unit
  def entityProcessed(entity: Path)(implicit operation: Operation.Id): Unit
  def metadataApplied(entity: Path)(implicit operation: Operation.Id): Unit
  def failureEncountered(failure: Throwable)(implicit operation: Operation.Id): Unit
  def completed()(implicit operation: Operation.Id): Unit
}
