package stasis.client.ops.scheduling

import java.nio.file.Path
import java.time.Instant

import akka.Done
import akka.actor.typed.scaladsl.LoggerOps
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.stream.{Materializer, SystemMaterializer}
import akka.util.Timeout
import org.slf4j.{Logger, LoggerFactory}
import stasis.client.collection.rules.Specification
import stasis.client.encryption.secrets.DeviceSecret
import stasis.client.ops.exceptions.OperationExecutionFailure
import stasis.client.ops.{backup, recovery, ParallelismConfig}
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.shared.ops.Operation

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class DefaultOperationExecutor(
  config: DefaultOperationExecutor.Config,
  secret: DeviceSecret
)(
  implicit system: ActorSystem[SpawnProtocol.Command],
  parallelismConfig: ParallelismConfig,
  timeout: Timeout,
  backupProviders: backup.Providers,
  recoveryProviders: recovery.Providers
) extends OperationExecutor {

  private implicit val ec: ExecutionContext = system.executionContext
  private implicit val mat: Materializer = SystemMaterializer(system).materializer

  private val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private val activeOperations: MemoryBackend[Operation.Id, Operation] =
    MemoryBackend(name = "executor-operations-store")

  override def operations: Future[Map[Operation.Id, Operation.Type]] =
    activeOperations.entries.map(_.mapValues(_.`type`))

  override def rules: Future[Specification] =
    SchedulingConfig.rules(file = config.backup.rulesFile).map(Specification.apply)

  override def startBackupWithRules(
    definition: DatasetDefinition.Id
  ): Future[Operation.Id] =
    for {
      _ <- requireUniqueOperation(ofType = Operation.Type.Backup)
      rules <- SchedulingConfig.rules(file = config.backup.rulesFile)
      descriptor <- backup.Backup.Descriptor(
        definition = definition,
        collector = backup.Backup.Descriptor.Collector.WithRules(spec = Specification(rules = rules)),
        deviceSecret = secret,
        limits = config.backup.limits
      )
      operation = new backup.Backup(descriptor = descriptor)
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
      operation = new backup.Backup(descriptor = descriptor)
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
      operation = new recovery.Recovery(descriptor = descriptor)
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
      operation = new recovery.Recovery(descriptor = descriptor)
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

  override def stop(operation: Operation.Id): Future[Done] =
    activeOperations.get(operation).flatMap {
      case Some(operation) =>
        operation.stop()
        Future.successful(Done)

      case None =>
        val message = s"Failed to stop [$operation]; operation not found"
        log.errorN(message)
        Future.failed(new OperationExecutionFailure(message))
    }

  private def requireUniqueOperation(ofType: Operation.Type): Future[Done] =
    operations.flatMap { active =>
      active.find(_._2 == ofType) match {
        case Some((operation, _)) =>
          val message = s"Cannot start [$ofType] operation; [$ofType] with ID [$operation] is already active"
          log.errorN(message)
          Future.failed(new OperationExecutionFailure(message))

        case None =>
          Future.successful(Done)
      }
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
          case NonFatal(e) =>
            log.errorN(
              "Failure encountered when running operation [{}]: [{}: {}]",
              operation.id,
              e.getClass.getSimpleName,
              e.getMessage,
              e
            )
            Done
        }
        .flatMap { _ =>
          activeOperations.delete(operation.id).map(_ => Done)
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
