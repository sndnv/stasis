package stasis.client.tracking

import akka.NotUsed
import akka.stream.scaladsl.Source
import stasis.client.model.TargetEntity
import stasis.client.tracking.state.RecoveryState
import stasis.shared.ops.Operation

import java.nio.file.Path
import scala.concurrent.Future

trait RecoveryTracker extends RecoveryTracker.View {
  def entityExamined(entity: Path, metadataChanged: Boolean, contentChanged: Boolean)(implicit operation: Operation.Id): Unit
  def entityCollected(entity: TargetEntity)(implicit operation: Operation.Id): Unit
  def entityProcessingStarted(entity: Path, expectedParts: Int)(implicit operation: Operation.Id): Unit
  def entityPartProcessed(entity: Path)(implicit operation: Operation.Id): Unit
  def entityProcessed(entity: Path)(implicit operation: Operation.Id): Unit
  def metadataApplied(entity: Path)(implicit operation: Operation.Id): Unit
  def failureEncountered(entity: Path, failure: Throwable)(implicit operation: Operation.Id): Unit
  def failureEncountered(failure: Throwable)(implicit operation: Operation.Id): Unit
  def completed()(implicit operation: Operation.Id): Unit
}

object RecoveryTracker {
  trait View {
    def state: Future[Map[Operation.Id, RecoveryState]]
    def updates(operation: Operation.Id): Source[RecoveryState, NotUsed]
  }
}
