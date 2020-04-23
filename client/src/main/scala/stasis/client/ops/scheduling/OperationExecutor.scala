package stasis.client.ops.scheduling

import java.nio.file.Path
import java.time.Instant

import akka.Done
import stasis.client.collection.rules.Specification
import stasis.client.ops.recovery
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.shared.ops.Operation

import scala.concurrent.Future

trait OperationExecutor {

  def operations: Future[Map[Operation.Id, Operation.Type]]
  def rules: Future[Specification]

  def startBackupWithRules(
    definition: DatasetDefinition.Id
  ): Future[Operation.Id]

  def startBackupWithEntities(
    definition: DatasetDefinition.Id,
    entities: Seq[Path]
  ): Future[Operation.Id]

  def startRecoveryWithDefinition(
    definition: DatasetDefinition.Id,
    until: Option[Instant],
    query: Option[recovery.Recovery.PathQuery],
    destination: Option[recovery.Recovery.Destination]
  ): Future[Operation.Id]

  def startRecoveryWithEntry(
    entry: DatasetEntry.Id,
    query: Option[recovery.Recovery.PathQuery],
    destination: Option[recovery.Recovery.Destination]
  ): Future[Operation.Id]

  def startExpiration(): Future[Operation.Id]

  def startValidation(): Future[Operation.Id]

  def startKeyRotation(): Future[Operation.Id]

  def stop(operation: Operation.Id): Future[Done]
}
