package stasis.client.ops.scheduling

import java.nio.file.Path
import java.time.Instant

import akka.Done
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.actor.typed.scaladsl.adapter._
import akka.event.{Logging, LoggingAdapter}
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.Timeout
import stasis.client.collection.rules.Specification
import stasis.client.encryption.secrets.DeviceSecret
import stasis.client.ops.{backup, recovery, ParallelismConfig}
import stasis.client.ops.exceptions.OperationExecutionFailure
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.shared.ops.Operation

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class DefaultOperationExecutor(
  config: DefaultOperationExecutor.Config,
  secret: DeviceSecret
)(
  implicit system: ActorSystem[SpawnProtocol],
  parallelismConfig: ParallelismConfig,
  timeout: Timeout,
  backupProviders: backup.Providers,
  recoveryProviders: recovery.Providers
) extends OperationExecutor {

  private implicit val untypedSystem: akka.actor.ActorSystem = system.toUntyped
  private implicit val ec: ExecutionContext = system.executionContext
  private implicit val mat: Materializer = ActorMaterializer()

  private val log: LoggingAdapter = Logging(untypedSystem, this.getClass.getName)

  private val activeOperations: MemoryBackend[Operation.Id, Operation] =
    MemoryBackend(name = "executor-operations-store")

  override def operations: Future[Map[Operation.Id, Operation.Type]] =
    activeOperations.entries.map(_.mapValues(_.`type`))

  override def startBackupWithRules(
    definition: DatasetDefinition.Id
  ): Future[Operation.Id] =
    for {
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
        log.error(message)
        Future.failed(new OperationExecutionFailure(message))
    }

  implicit class RunnableOperation(operation: Operation) {
    def run(): Unit = {
      val _ = runWithResult()
    }

    def runWithResult(): Future[Done] = {
      log.debug("Starting operation [{}]", operation.id)
      operation
        .start()
        .recover {
          case NonFatal(e) =>
            log.error(
              e,
              "Failure encountered when running operation [{}]: [{}]",
              operation.id,
              e.getMessage
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
