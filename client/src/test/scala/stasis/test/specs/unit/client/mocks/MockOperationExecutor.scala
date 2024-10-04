package stasis.test.specs.unit.client.mocks

import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.Future

import org.apache.pekko.Done

import stasis.client.collection.rules.Rule
import stasis.client.collection.rules.Specification
import stasis.client.collection.rules.internal.IndexedRule
import stasis.client.ops.recovery.Recovery
import stasis.client.ops.scheduling.OperationExecutor
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.datasets.DatasetEntry
import stasis.shared.ops.Operation
import stasis.test.specs.unit.client.mocks.MockOperationExecutor.Statistic

class MockOperationExecutor() extends OperationExecutor {
  private val stats: Map[Statistic, AtomicInteger] = Map(
    Statistic.GetActiveOperations -> new AtomicInteger(0),
    Statistic.GetCompletedOperations -> new AtomicInteger(0),
    Statistic.GetRules -> new AtomicInteger(0),
    Statistic.StartBackupWithRules -> new AtomicInteger(0),
    Statistic.StartBackupWithFiles -> new AtomicInteger(0),
    Statistic.ResumeBackupWithState -> new AtomicInteger(0),
    Statistic.StartRecoveryWithDefinition -> new AtomicInteger(0),
    Statistic.StartRecoveryWithEntry -> new AtomicInteger(0),
    Statistic.StartExpiration -> new AtomicInteger(0),
    Statistic.StartValidation -> new AtomicInteger(0),
    Statistic.StartKeyRotation -> new AtomicInteger(0),
    Statistic.Stop -> new AtomicInteger(0)
  )

  override def active: Future[Map[Operation.Id, Operation.Type]] = {
    stats(Statistic.GetActiveOperations).incrementAndGet()
    Future.successful(
      Map(
        Operation.generateId() -> Operation.Type.Backup,
        Operation.generateId() -> Operation.Type.Recovery,
        Operation.generateId() -> Operation.Type.GarbageCollection
      )
    )
  }

  override def completed: Future[Map[Operation.Id, Operation.Type]] = {
    stats(Statistic.GetCompletedOperations).incrementAndGet()
    Future.successful(
      Map(
        Operation.generateId() -> Operation.Type.Backup,
        Operation.generateId() -> Operation.Type.GarbageCollection
      )
    )
  }

  override def rules: Future[Specification] = {
    stats(Statistic.GetRules).incrementAndGet()

    val rule1 = Rule(line = "+ /tmp/file *", lineNumber = 0).get
    val explanation = Specification.Entry.Explanation(operation = Rule.Operation.Include, original = rule1.original)

    val rule2 = Rule(line = "+ /tmp/other *", lineNumber = 1).get

    val file1 = Paths.get("/tmp/file/01")
    val file2 = Paths.get("/tmp/file/02")

    val spec = Specification(
      entries = Map(
        file1 -> Specification.Entry(
          file = file1,
          directory = file1.getParent,
          operation = Rule.Operation.Include,
          reason = Seq(explanation)
        ),
        file2 -> Specification.Entry(
          file = file2,
          directory = file2.getParent,
          operation = Rule.Operation.Include,
          reason = Seq(explanation)
        )
      ),
      failures = Seq(
        Specification.FailedMatch(
          rule = IndexedRule(index = 0, underlying = rule2),
          path = Paths.get("/tmp/other"),
          failure = new RuntimeException("Test failure")
        )
      )
    )
    Future.successful(spec)
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

  override def resumeBackup(operation: Operation.Id): Future[Operation.Id] = {
    stats(Statistic.ResumeBackupWithState).incrementAndGet()
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

  override def stop(operation: Operation.Id): Future[Option[Done]] = {
    stats(Statistic.Stop).incrementAndGet()
    Future.successful(Some(Done))
  }

  def statistics: Map[Statistic, Int] = stats.view.mapValues(_.get()).toMap
}

object MockOperationExecutor {
  def apply(): MockOperationExecutor = new MockOperationExecutor()

  sealed trait Statistic
  object Statistic {
    case object GetActiveOperations extends Statistic
    case object GetCompletedOperations extends Statistic
    case object GetRules extends Statistic
    case object StartBackupWithRules extends Statistic
    case object StartBackupWithFiles extends Statistic
    case object ResumeBackupWithState extends Statistic
    case object StartRecoveryWithDefinition extends Statistic
    case object StartRecoveryWithEntry extends Statistic
    case object StartExpiration extends Statistic
    case object StartValidation extends Statistic
    case object StartKeyRotation extends Statistic
    case object Stop extends Statistic
  }
}
