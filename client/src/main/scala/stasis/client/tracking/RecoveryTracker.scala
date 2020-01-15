package stasis.client.tracking

import java.nio.file.Path

import stasis.shared.ops.Operation

trait RecoveryTracker {
  def fileCollected(file: Path)(implicit operation: Operation.Id): Unit
  def fileProcessed(file: Path)(implicit operation: Operation.Id): Unit
  def metadataApplied(file: Path)(implicit operation: Operation.Id): Unit
  def failureEncountered(failure: Throwable)(implicit operation: Operation.Id): Unit
  def completed()(implicit operation: Operation.Id): Unit
}
