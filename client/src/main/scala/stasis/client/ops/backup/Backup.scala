package stasis.client.ops.backup

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.stream._
import akka.stream.scaladsl.{Sink, Source}
import akka.{Done, NotUsed}
import stasis.client.collection.rules.Rule
import stasis.client.encryption.secrets.DeviceSecret
import stasis.client.model.DatasetMetadata
import stasis.client.ops.ParallelismConfig
import stasis.client.ops.backup.stages._
import stasis.client.ops.exceptions.{EntityProcessingFailure, OperationStopped}
import stasis.client.tracking.BackupTracker
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.shared.ops.Operation

import java.nio.file.Path
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class Backup(
  descriptor: Backup.Descriptor
)(implicit system: ActorSystem[SpawnProtocol.Command], parallelism: ParallelismConfig, providers: Providers)
    extends Operation { parent =>
  import Backup._

  private implicit val mat: Materializer = SystemMaterializer(system).materializer

  private val supervision: Supervision.Decider = {
    case e: EntityProcessingFailure =>
      system.log.error(
        "Backup stream encountered failure while processing entity [{}]: [{} - {}]; resuming",
        e.entity,
        e.cause.getClass.getSimpleName,
        e.cause.getMessage
      )
      providers.track.failureEncountered(e.entity, failure = e.cause)
      Supervision.Resume

    case e =>
      system.log.error("Backup stream encountered failure: [{} - {}]; resuming", e.getClass.getSimpleName, e.getMessage)
      providers.track.failureEncountered(failure = e)
      Supervision.Resume
  }

  private implicit val ec: ExecutionContext = system.executionContext

  override implicit val id: Operation.Id = Operation.generateId()

  override val `type`: Operation.Type = Operation.Type.Backup

  private implicit val killSwitch: SharedKillSwitch = KillSwitches.shared("backup-kill-switch")

  private val stream: Source[Done, NotUsed] =
    stages.entityDiscovery
      .via(stages.entityCollection)
      .via(stages.entityProcessing)
      .via(stages.metadataCollection)
      .via(stages.metadataPush)
      .via(killSwitch.flow)
      .withAttributes(ActorAttributes.supervisionStrategy(supervision))

  override def start(): Future[Done] =
    stream
      .runWith(Sink.ignore)
      .trackWith(providers.track)

  override def stop(): Unit =
    killSwitch.abort(OperationStopped(s"Operation [${id.toString}] stopped by user"))

  private object stages
      extends EntityDiscovery
      with EntityCollection
      with EntityProcessing
      with MetadataCollection
      with MetadataPush {
    override protected lazy val collector: EntityDiscovery.Collector = parent.descriptor.collector.asDiscoveryCollector
    override protected lazy val targetDataset: DatasetDefinition = parent.descriptor.targetDataset
    override protected lazy val latestEntry: Option[DatasetEntry] = parent.descriptor.latestEntry
    override protected lazy val latestMetadata: Option[DatasetMetadata] = parent.descriptor.latestMetadata
    override protected lazy val deviceSecret: DeviceSecret = parent.descriptor.deviceSecret
    override protected lazy val providers: Providers = parent.providers
    override protected lazy val parallelism: ParallelismConfig = parent.parallelism
    override protected lazy val maxChunkSize: Int = parent.descriptor.limits.maxChunkSize
    override protected lazy val maxPartSize: Long = parent.descriptor.limits.maxPartSize
    override implicit protected lazy val mat: Materializer = parent.mat
    override implicit protected lazy val ec: ExecutionContext = parent.system.executionContext
  }
}

object Backup {
  def apply(
    descriptor: Backup.Descriptor
  )(implicit system: ActorSystem[SpawnProtocol.Command], parallelism: ParallelismConfig, providers: Providers): Backup =
    new Backup(descriptor)

  final case class Descriptor(
    targetDataset: DatasetDefinition,
    latestEntry: Option[DatasetEntry],
    latestMetadata: Option[DatasetMetadata],
    deviceSecret: DeviceSecret,
    collector: Descriptor.Collector,
    limits: Limits
  )

  object Descriptor {
    sealed trait Collector {
      def asDiscoveryCollector: EntityDiscovery.Collector
    }
    object Collector {
      final case class WithRules(rules: Seq[Rule]) extends Collector {
        override def asDiscoveryCollector: EntityDiscovery.Collector =
          EntityDiscovery.Collector.WithRules(rules)
      }

      final case class WithEntities(entities: Seq[Path]) extends Collector {
        override def asDiscoveryCollector: EntityDiscovery.Collector =
          EntityDiscovery.Collector.WithEntities(entities)
      }
    }

    def apply(
      definition: DatasetDefinition.Id,
      collector: Descriptor.Collector,
      deviceSecret: DeviceSecret,
      limits: Limits
    )(implicit ec: ExecutionContext, providers: Providers): Future[Descriptor] =
      for {
        targetDataset <- providers.clients.api.datasetDefinition(definition = definition)
        latestEntry <- providers.clients.api.latestEntry(definition = definition, until = None)
        latestMetadata <- latestEntry match {
          case Some(latestEntry) => providers.clients.api.datasetMetadata(entry = latestEntry).map(Some.apply)
          case None              => Future.successful(None)
        }
      } yield {
        Descriptor(
          targetDataset = targetDataset,
          latestEntry = latestEntry,
          latestMetadata = latestMetadata,
          deviceSecret = deviceSecret,
          collector = collector,
          limits = limits
        )
      }
  }

  final case class Limits(
    maxChunkSize: Int,
    maxPartSize: Long
  )

  implicit class TrackedOperation(operation: Future[Done]) {
    def trackWith(track: BackupTracker)(implicit id: Operation.Id, ec: ExecutionContext): Future[Done] = {
      operation.onComplete {
        case Success(_) | Failure(_: OperationStopped) =>
          track.completed()

        case Failure(e: EntityProcessingFailure) =>
          track.failureEncountered(entity = e.entity, failure = e.cause)
          track.completed()

        case Failure(e) =>
          track.failureEncountered(failure = e)
          track.completed()
      }

      operation
    }
  }
}
