package stasis.client.ops.recovery

import java.nio.file.{FileSystem, FileSystems, Path}
import java.time.Instant

import akka.{Done, NotUsed}
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.actor.typed.scaladsl.adapter._
import akka.stream._
import akka.stream.scaladsl.{Keep, Sink, Source}
import stasis.client.collection.{RecoveryCollector, RecoveryMetadataCollector}
import stasis.client.encryption.secrets.DeviceSecret
import stasis.client.model.{DatasetMetadata, TargetFile}
import stasis.client.ops.recovery.stages.{FileCollection, FileProcessing, MetadataApplication}
import stasis.client.ops.ParallelismConfig
import stasis.client.tracking.RecoveryTracker
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.shared.ops.Operation

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scala.util.matching.Regex

class Recovery(
  descriptor: Recovery.Descriptor
)(implicit system: ActorSystem[SpawnProtocol], parallelism: ParallelismConfig, providers: Providers)
    extends Operation { parent =>
  import Recovery._

  private implicit val untypedSystem: akka.actor.ActorSystem = system.toUntyped

  private implicit val mat: Materializer = ActorMaterializer(
    ActorMaterializerSettings(untypedSystem).withSupervisionStrategy { e =>
      system.log.error(e, "Recovery stream encountered failure: [{}]; resuming", e.getMessage)
      providers.track.failureEncountered(failure = e)
      Supervision.Resume
    }
  )

  private implicit val ec: ExecutionContext = system.executionContext

  override implicit val id: Operation.Id = Operation.generateId()

  override val `type`: Operation.Type = Operation.Type.Recovery

  private val collector: RecoveryCollector = parent.descriptor.toRecoveryCollector()

  private val (killSwitch: UniqueKillSwitch, stream: Source[Done, NotUsed]) =
    stages.fileCollection
      .via(stages.fileProcessing)
      .via(stages.metadataApplication)
      .viaMat(KillSwitches.single)(Keep.right[NotUsed, UniqueKillSwitch])
      .preMaterialize()

  override def start(): Future[Done] =
    stream
      .runWith(Sink.ignore)
      .trackWith(providers.track)

  override def stop(): Unit =
    killSwitch.shutdown()

  private object stages extends FileCollection with FileProcessing with MetadataApplication {
    override protected lazy val deviceSecret: DeviceSecret = parent.descriptor.deviceSecret
    override protected lazy val providers: Providers = parent.providers
    override protected lazy val collector: RecoveryCollector = parent.collector
    override protected lazy val parallelism: ParallelismConfig = parent.parallelism
    override implicit protected lazy val mat: Materializer = parent.mat
    override implicit protected lazy val ec: ExecutionContext = parent.system.executionContext
  }
}

object Recovery {
  final case class Descriptor(
    targetMetadata: DatasetMetadata,
    query: Option[PathQuery],
    destination: Option[Destination],
    deviceSecret: DeviceSecret
  ) {
    def toRecoveryCollector()(
      implicit ec: ExecutionContext,
      mat: Materializer,
      parallelism: ParallelismConfig,
      providers: Providers
    ): RecoveryCollector =
      new RecoveryCollector.Default(
        targetMetadata = targetMetadata,
        keep = (file, _) => query.forall(_.matches(file.toAbsolutePath)),
        destination = destination.toTargetFileDestination,
        metadataCollector = new RecoveryMetadataCollector.Default(checksum = providers.checksum),
        api = providers.clients.api
      )
  }

  object Descriptor {
    sealed trait Collector
    object Collector {
      final case class WithDefinition(definition: DatasetDefinition.Id, until: Option[Instant]) extends Collector
      final case class WithEntry(entry: DatasetEntry.Id) extends Collector
    }

    def apply(
      query: Option[PathQuery],
      destination: Option[Destination],
      collector: Descriptor.Collector,
      deviceSecret: DeviceSecret
    )(implicit ec: ExecutionContext, mat: Materializer, providers: Providers): Future[Descriptor] =
      for {
        entry <- collector match {
          case Collector.WithDefinition(definition, until) =>
            providers.clients.api.latestEntry(definition, until).flatMap {
              case Some(entry) =>
                Future.successful(entry)

              case None =>
                Future.failed(
                  new IllegalStateException(
                    s"Expected dataset entry for definition [$definition] but none was found"
                  )
                )
            }

          case Collector.WithEntry(entry) =>
            providers.clients.api.datasetEntry(entry)
        }
        metadata <- providers.clients.api.datasetMetadata(entry)
      } yield {
        Descriptor(
          targetMetadata = metadata,
          query = query,
          destination = destination,
          deviceSecret = deviceSecret
        )
      }
  }

  sealed trait PathQuery {
    def matches(path: Path): Boolean
  }

  object PathQuery {
    def apply(query: String): PathQuery =
      if (query.contains("/")) {
        ForAbsolutePath(query = new Regex(query))
      } else {
        ForFileName(query = new Regex(query))
      }

    final case class ForAbsolutePath(query: Regex) extends PathQuery {
      override def matches(path: Path): Boolean =
        query.pattern.matcher(path.toAbsolutePath.toString).find()
    }

    final case class ForFileName(query: Regex) extends PathQuery {
      override def matches(path: Path): Boolean =
        query.pattern.matcher(path.getFileName.toString).find()
    }
  }

  final case class Destination(
    path: String,
    keepStructure: Boolean
  )

  implicit class RecoveryToTargetFileDestination(destination: Option[Destination]) {
    def toTargetFileDestination: TargetFile.Destination =
      toTargetFileDestination(filesystem = FileSystems.getDefault)

    def toTargetFileDestination(filesystem: FileSystem): TargetFile.Destination =
      destination match {
        case Some(destination) =>
          TargetFile.Destination.Directory(
            path = filesystem.getPath(destination.path),
            keepDefaultStructure = destination.keepStructure
          )

        case None =>
          TargetFile.Destination.Default
      }
  }

  implicit class TrackedOperation(operation: Future[Done]) {
    def trackWith(track: RecoveryTracker)(implicit id: Operation.Id, ec: ExecutionContext): Future[Done] = {
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
