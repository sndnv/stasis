package stasis.client.ops.backup

import java.nio.file.Path

import akka.{Done, NotUsed}
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.actor.typed.scaladsl.adapter._
import akka.stream._
import akka.stream.scaladsl.{Keep, Sink, Source}
import stasis.client.analysis.Checksum
import stasis.client.collection.{BackupCollector, BackupMetadataCollector}
import stasis.client.collection.rules.Specification
import stasis.client.encryption.secrets.DeviceSecret
import stasis.client.model.DatasetMetadata
import stasis.client.ops.ParallelismConfig
import stasis.client.ops.backup.stages.{FileCollection, FileProcessing, MetadataCollection, MetadataPush}
import stasis.client.tracking.BackupTracker
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.shared.ops.Operation

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class Backup(
  descriptor: Backup.Descriptor
)(implicit system: ActorSystem[SpawnProtocol], parallelism: ParallelismConfig, providers: Providers)
    extends Operation { parent =>
  import Backup._

  private implicit val untypedSystem: akka.actor.ActorSystem = system.toUntyped

  private implicit val mat: Materializer = ActorMaterializer(
    ActorMaterializerSettings(untypedSystem).withSupervisionStrategy { e =>
      system.log.error(e, "Backup stream encountered failure: [{}]; resuming", e.getMessage)
      providers.track.failureEncountered(failure = e)
      Supervision.Resume
    }
  )

  private implicit val ec: ExecutionContext = system.executionContext

  override implicit val id: Operation.Id = Operation.generateId()

  override val `type`: Operation.Type = Operation.Type.Backup

  private val collector: BackupCollector = parent.descriptor.toBackupCollector(parent.providers.checksum)

  private val (killSwitch: UniqueKillSwitch, stream: Source[Done, NotUsed]) =
    stages.fileCollection
      .via(stages.fileProcessing)
      .via(stages.metadataCollection)
      .via(stages.metadataPush)
      .viaMat(KillSwitches.single)(Keep.right[NotUsed, UniqueKillSwitch])
      .preMaterialize()

  override def start(): Future[Done] =
    stream
      .runWith(Sink.ignore)
      .trackWith(providers.track)

  override def stop(): Unit =
    killSwitch.shutdown()

  private object stages extends FileCollection with FileProcessing with MetadataCollection with MetadataPush {
    override protected lazy val targetDataset: DatasetDefinition = parent.descriptor.targetDataset
    override protected lazy val latestEntry: Option[DatasetEntry] = parent.descriptor.latestEntry
    override protected lazy val latestMetadata: Option[DatasetMetadata] = parent.descriptor.latestMetadata
    override protected lazy val deviceSecret: DeviceSecret = parent.descriptor.deviceSecret
    override protected lazy val providers: Providers = parent.providers
    override protected lazy val collector: BackupCollector = parent.collector
    override protected lazy val parallelism: ParallelismConfig = parent.parallelism
    override implicit protected lazy val mat: Materializer = parent.mat
    override implicit protected lazy val ec: ExecutionContext = parent.system.executionContext
  }
}

object Backup {
  final case class Descriptor(
    targetDataset: DatasetDefinition,
    latestEntry: Option[DatasetEntry],
    latestMetadata: Option[DatasetMetadata],
    deviceSecret: DeviceSecret,
    collector: Descriptor.Collector
  ) {
    def toBackupCollector(
      checksum: Checksum
    )(implicit mat: Materializer, parallelism: ParallelismConfig): BackupCollector =
      collector match {
        case Descriptor.Collector.WithRules(spec) =>
          new BackupCollector.Default(
            files = spec.included.toList,
            metadataCollector = new BackupMetadataCollector.Default(
              checksum = checksum,
              latestMetadata = latestMetadata
            )
          )

        case Descriptor.Collector.WithFiles(files) =>
          new BackupCollector.Default(
            files = files.toList,
            metadataCollector = new BackupMetadataCollector.Default(
              checksum = checksum,
              latestMetadata = latestMetadata
            )
          )
      }
  }

  object Descriptor {
    sealed trait Collector
    object Collector {
      final case class WithRules(spec: Specification) extends Collector
      final case class WithFiles(files: Seq[Path]) extends Collector
    }

    def apply(
      definition: DatasetDefinition.Id,
      collector: Descriptor.Collector,
      deviceSecret: DeviceSecret
    )(implicit ec: ExecutionContext, mat: Materializer, providers: Providers): Future[Descriptor] =
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
          collector = collector
        )
      }
  }

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