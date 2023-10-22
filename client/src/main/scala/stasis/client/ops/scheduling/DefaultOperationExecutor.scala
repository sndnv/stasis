package stasis.client.ops.scheduling

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.actor.typed.{ActorSystem, SpawnProtocol}
import org.apache.pekko.util.Timeout
import org.slf4j.{Logger, LoggerFactory}
import stasis.client.collection.rules.Specification
import stasis.client.encryption.secrets.DeviceSecret
import stasis.client.ops.exceptions.{OperationExecutionFailure, OperationStopped}
import stasis.client.ops.{backup, recovery, ParallelismConfig}
import stasis.client.tracking.state.OperationState
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.core.telemetry.TelemetryContext
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.shared.ops.Operation

import java.nio.file.Path
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class DefaultOperationExecutor(
  config: DefaultOperationExecutor.Config,
  secret: DeviceSecret
)(implicit
  system: ActorSystem[SpawnProtocol.Command],
  telemetry: TelemetryContext,
  parallelismConfig: ParallelismConfig,
  timeout: Timeout,
  backupProviders: backup.Providers,
  recoveryProviders: recovery.Providers
) extends OperationExecutor {

  private implicit val ec: ExecutionContext = system.executionContext

  private val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private val activeOperations: MemoryBackend[Operation.Id, Operation] =
    MemoryBackend(name = "executor-active-operations-store")

  private val completedOperations: MemoryBackend[Operation.Id, Operation.Type] =
    MemoryBackend(name = "executor-completed-operations-store")

  override def active: Future[Map[Operation.Id, Operation.Type]] =
    activeOperations.entries.map(_.map { case (id, operation) => (id, operation.`type`) })

  override def completed: Future[Map[Operation.Id, Operation.Type]] =
    completedOperations.entries

  override def rules: Future[Specification] =
    SchedulingConfig.rules(file = config.backup.rulesFile).flatMap(Specification.untracked)

  override def startBackupWithRules(
    definition: DatasetDefinition.Id
  ): Future[Operation.Id] =
    for {
      _ <- requireUniqueOperation(ofType = Operation.Type.Backup)
      rules <- SchedulingConfig.rules(file = config.backup.rulesFile)
      descriptor <- backup.Backup.Descriptor(
        definition = definition,
        collector = backup.Backup.Descriptor.Collector.WithRules(rules),
        deviceSecret = secret,
        limits = config.backup.limits
      )
      operation = backup.Backup(descriptor = descriptor)
      _ <- activeOperations.put(operation.id, operation)
    } yield {
      operation.run()
      operation.id
    }

  override def startBackupWithEntities(
    definition: DatasetDefinition.Id,
    entities: Seq[Path]
  ): Future[Operation.Id] =
    for {
      _ <- requireUniqueOperation(ofType = Operation.Type.Backup)
      descriptor <- backup.Backup.Descriptor(
        definition = definition,
        collector = backup.Backup.Descriptor.Collector.WithEntities(entities = entities),
        deviceSecret = secret,
        limits = config.backup.limits
      )
      operation = backup.Backup(descriptor = descriptor)
      _ <- activeOperations.put(operation.id, operation)
    } yield {
      operation.run()
      operation.id
    }

  override def resumeBackup(
    operation: Operation.Id
  ): Future[Operation.Id] =
    for {
      _ <- requireUniqueOperation(ofType = Operation.Type.Backup)
      state <- backupProviders.track.state.map(_.get(operation))
      state <- requireValidOperationState(operation = operation, state = state)
      descriptor <- backup.Backup.Descriptor(
        definition = state.definition,
        collector = backup.Backup.Descriptor.Collector.WithState(state = state),
        deviceSecret = secret,
        limits = config.backup.limits
      )
      operation = backup.Backup(descriptor = descriptor)
      _ <- activeOperations.put(operation.id, operation)
    } yield {
      operation.run()
      operation.id
    }

  override def startRecoveryWithDefinition(
    definition: DatasetDefinition.Id,
    until: Option[Instant],
    query: Option[recovery.Recovery.PathQuery],
    destination: Option[recovery.Recovery.Destination]
  ): Future[Operation.Id] =
    for {
      _ <- requireUniqueOperation(ofType = Operation.Type.Recovery)
      descriptor <- recovery.Recovery.Descriptor(
        collector = recovery.Recovery.Descriptor.Collector.WithDefinition(definition, until),
        query = query,
        destination = destination,
        deviceSecret = secret
      )
      operation = recovery.Recovery(descriptor = descriptor)
      _ <- activeOperations.put(operation.id, operation)
    } yield {
      operation.run()
      operation.id
    }

  override def startRecoveryWithEntry(
    entry: DatasetEntry.Id,
    query: Option[recovery.Recovery.PathQuery],
    destination: Option[recovery.Recovery.Destination]
  ): Future[Operation.Id] =
    for {
      _ <- requireUniqueOperation(ofType = Operation.Type.Recovery)
      descriptor <- recovery.Recovery.Descriptor(
        query = query,
        destination = destination,
        collector = recovery.Recovery.Descriptor.Collector.WithEntry(entry),
        deviceSecret = secret
      )
      operation = recovery.Recovery(descriptor = descriptor)
      _ <- activeOperations.put(operation.id, operation)
    } yield {
      operation.run()
      operation.id
    }

  override def startExpiration(): Future[Operation.Id] =
    Future.failed(new NotImplementedError("Expiration is not supported")) // TODO - implement

  override def startValidation(): Future[Operation.Id] =
    Future.failed(new NotImplementedError("Validation is not supported")) // TODO - implement

  override def startKeyRotation(): Future[Operation.Id] =
    Future.failed(new NotImplementedError("Key rotation is not supported")) // TODO - implement

  override def stop(operation: Operation.Id): Future[Option[Done]] =
    activeOperations
      .get(operation)
      .map(_.map { operation =>
        operation.stop()
        Done
      })

  private def requireUniqueOperation(ofType: Operation.Type): Future[Done] =
    active.flatMap { active =>
      active.find(_._2 == ofType) match {
        case Some((operation, _)) =>
          val opType = ofType.toString
          val message = s"Cannot start [$opType] operation; [$opType] with ID [${operation.toString}] is already active"
          log.errorN(message)
          Future.failed(new OperationExecutionFailure(message))

        case None =>
          Future.successful(Done)
      }
    }

  private def requireValidOperationState[S <: OperationState](
    operation: Operation.Id,
    state: Option[S]
  ): Future[S] =
    state match {
      case Some(state) if !state.isCompleted =>
        Future.successful(state)

      case Some(_) =>
        val message = s"Cannot resume operation with ID [${operation.toString}]; operation already completed"
        log.errorN(message)
        Future.failed(new OperationExecutionFailure(message))

      case None =>
        val message = s"Cannot resume operation with ID [${operation.toString}]; no existing state was found"
        log.errorN(message)
        Future.failed(new OperationExecutionFailure(message))
    }

  implicit class RunnableOperation(operation: Operation) {
    def run(): Unit = {
      val _ = runWithResult()
    }

    def runWithResult(): Future[Done] = {
      log.debugN("Starting operation [{}]", operation.id)
      operation
        .start()
        .recover {
          case NonFatal(e: OperationStopped) =>
            log.debugN(e.message)
            Done

          case NonFatal(e) =>
            log.errorN(
              "Failure encountered when running operation [{}]: [{} - {}]",
              operation.id,
              e.getClass.getSimpleName,
              e.getMessage
            )
            Done
        }
        .flatMap { _ =>
          for {
            _ <- activeOperations.delete(operation.id)
            _ <- completedOperations.put(operation.id, operation.`type`)
          } yield {
            Done
          }
        }
    }
  }
}

object DefaultOperationExecutor {
  final case class Config(
    backup: Config.Backup
  )

  object Config {
    final case class Backup(
      rulesFile: Path,
      limits: backup.Backup.Limits
    )
  }
}
