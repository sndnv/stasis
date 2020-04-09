package stasis.test.specs.unit.client.mocks

import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

import akka.Done
import stasis.client.ops.recovery.Recovery
import stasis.client.ops.scheduling.OperationExecutor
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.datasets.DatasetEntry
import stasis.shared.ops.Operation
import stasis.test.specs.unit.client.mocks.MockOperationExecutor.Statistic

import scala.concurrent.Future

class MockOperationExecutor extends OperationExecutor {
  private val stats: Map[Statistic, AtomicInteger] = Map(
    Statistic.GetOperations -> new AtomicInteger(0),
    Statistic.StartBackupWithRules -> new AtomicInteger(0),
    Statistic.StartBackupWithFiles -> new AtomicInteger(0),
    Statistic.StartRecoveryWithDefinition -> new AtomicInteger(0),
    Statistic.StartRecoveryWithEntry -> new AtomicInteger(0),
    Statistic.StartExpiration -> new AtomicInteger(0),
    Statistic.StartValidation -> new AtomicInteger(0),
    Statistic.StartKeyRotation -> new AtomicInteger(0),
    Statistic.Stop -> new AtomicInteger(0)
  )

  override def operations: Future[Map[Operation.Id, Operation.Type]] = {
    stats(Statistic.GetOperations).incrementAndGet()
    Future.successful(
      Map(
        Operation.generateId() -> Operation.Type.Backup,
        Operation.generateId() -> Operation.Type.Recovery,
        Operation.generateId() -> Operation.Type.GarbageCollection
      )
    )
  }

  override def startBackupWithRules(
    definition: DatasetDefinition.Id
  ): Future[Operation.Id] = {
    stats(Statistic.StartBackupWithRules).incrementAndGet()
    Future.successful(Operation.generateId())
  }

  override def startBackupWithEntities(
    definition: DatasetDefinition.Id,
    entities: Seq[Path]
  ): Future[Operation.Id] = {
    stats(Statistic.StartBackupWithFiles).incrementAndGet()
    Future.successful(Operation.generateId())
  }

  override def startRecoveryWithDefinition(
    definition: DatasetDefinition.Id,
    until: Option[Instant],
    query: Option[Recovery.PathQuery],
    destination: Option[Recovery.Destination]
  ): Future[Operation.Id] = {
    stats(Statistic.StartRecoveryWithDefinition).incrementAndGet()
    Future.successful(Operation.generateId())
  }

  override def startRecoveryWithEntry(
    entry: DatasetEntry.Id,
    query: Option[Recovery.PathQuery],
    destination: Option[Recovery.Destination]
  ): Future[Operation.Id] = {
    stats(Statistic.StartRecoveryWithEntry).incrementAndGet()
    Future.successful(Operation.generateId())
  }

  override def startExpiration(): Future[Operation.Id] = {
    stats(Statistic.StartExpiration).incrementAndGet()
    Future.successful(Operation.generateId())
  }

  override def startValidation(): Future[Operation.Id] = {
    stats(Statistic.StartValidation).incrementAndGet()
    Future.successful(Operation.generateId())
  }

  override def startKeyRotation(): Future[Operation.Id] = {
    stats(Statistic.StartKeyRotation).incrementAndGet()
    Future.successful(Operation.generateId())
  }

  override def stop(operation: Operation.Id): Future[Done] = {
    stats(Statistic.Stop).incrementAndGet()
    Future.successful(Done)
  }

  def statistics: Map[Statistic, Int] = stats.mapValues(_.get())
}

object MockOperationExecutor {
  def apply(): MockOperationExecutor = new MockOperationExecutor()

  sealed trait Statistic
  object Statistic {
    case object GetOperations extends Statistic
    case object StartBackupWithRules extends Statistic
    case object StartBackupWithFiles extends Statistic
    case object StartRecoveryWithDefinition extends Statistic
    case object StartRecoveryWithEntry extends Statistic
    case object StartExpiration extends Statistic
    case object StartValidation extends Statistic
    case object StartKeyRotation extends Statistic
    case object Stop extends Statistic
  }
}
