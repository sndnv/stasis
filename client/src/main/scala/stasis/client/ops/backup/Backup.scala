package stasis.client.ops.backup

import java.nio.file.Path
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.stream._
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.{Done, NotUsed}
import stasis.client.analysis.Checksum
import stasis.client.collection.rules.Specification
import stasis.client.collection.{BackupCollector, BackupMetadataCollector}
import stasis.client.encryption.secrets.DeviceSecret
import stasis.client.model.DatasetMetadata
import stasis.client.ops.ParallelismConfig
import stasis.client.ops.backup.stages.{EntityCollection, EntityProcessing, MetadataCollection, MetadataPush}
import stasis.client.tracking.BackupTracker
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.shared.ops.Operation

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class Backup(
  descriptor: Backup.Descriptor
)(implicit system: ActorSystem[SpawnProtocol.Command], parallelism: ParallelismConfig, providers: Providers)
    extends Operation { parent =>
  import Backup._

  private implicit val mat: Materializer = SystemMaterializer(system).materializer

  private val supervision: Supervision.Decider = { e =>
    system.log.error("Backup stream encountered failure: [{}: {}]; resuming", e.getClass.getSimpleName, e.getMessage, e)
    providers.track.failureEncountered(failure = e)
    Supervision.Resume
  }

  private implicit val ec: ExecutionContext = system.executionContext

  override implicit val id: Operation.Id = Operation.generateId()

  override val `type`: Operation.Type = Operation.Type.Backup

  private val collector: BackupCollector = parent.descriptor.toBackupCollector(parent.providers.checksum)

  private val (killSwitch: UniqueKillSwitch, stream: Source[Done, NotUsed]) =
    stages.entityCollection
      .via(stages.entityProcessing)
      .via(stages.metadataCollection)
      .via(stages.metadataPush)
      .viaMat(KillSwitches.single)(Keep.right[NotUsed, UniqueKillSwitch])
      .withAttributes(ActorAttributes.supervisionStrategy(supervision))
      .preMaterialize()

  override def start(): Future[Done] =
    stream
      .runWith(Sink.ignore)
      .trackWith(providers.track)

  override def stop(): Unit =
    killSwitch.shutdown()

  private object stages extends EntityCollection with EntityProcessing with MetadataCollection with MetadataPush {
    override protected lazy val targetDataset: DatasetDefinition = parent.descriptor.targetDataset
    override protected lazy val latestEntry: Option[DatasetEntry] = parent.descriptor.latestEntry
    override protected lazy val latestMetadata: Option[DatasetMetadata] = parent.descriptor.latestMetadata
    override protected lazy val deviceSecret: DeviceSecret = parent.descriptor.deviceSecret
    override protected lazy val providers: Providers = parent.providers
    override protected lazy val collector: BackupCollector = parent.collector
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
  ) {
    def toBackupCollector(
      checksum: Checksum
    )(implicit
      mat: Materializer,
      ec: ExecutionContext,
      parallelism: ParallelismConfig,
      providers: Providers
    ): BackupCollector =
      collector match {
        case Descriptor.Collector.WithRules(spec) =>
          new BackupCollector.Default(
            entities = spec.included.toList,
            latestMetadata = latestMetadata,
            metadataCollector = BackupMetadataCollector.Default(checksum = checksum),
            api = providers.clients.api
          )

        case Descriptor.Collector.WithEntities(entities) =>
          new BackupCollector.Default(
            entities = entities.toList,
            latestMetadata = latestMetadata,
            metadataCollector = BackupMetadataCollector.Default(checksum = checksum),
            api = providers.clients.api
          )
      }
  }

  object Descriptor {
    sealed trait Collector
    object Collector {
      final case class WithRules(spec: Specification) extends Collector
      final case class WithEntities(entities: Seq[Path]) extends Collector
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
        case Success(_) =>
          track.completed()

        case Failure(e) =>
          track.failureEncountered(failure = e)
          track.completed()
      }

      operation
    }
  }
}
