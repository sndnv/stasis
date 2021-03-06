package stasis.client.ops.recovery

import java.nio.file.{FileSystem, FileSystems, Path}
import java.time.Instant

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.stream._
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.{Done, NotUsed}
import stasis.client.collection.{RecoveryCollector, RecoveryMetadataCollector}
import stasis.client.encryption.secrets.DeviceSecret
import stasis.client.model.{DatasetMetadata, TargetEntity}
import stasis.client.ops.ParallelismConfig
import stasis.client.ops.recovery.stages.{EntityCollection, EntityProcessing, MetadataApplication}
import stasis.client.tracking.RecoveryTracker
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.shared.ops.Operation

import scala.concurrent.{ExecutionContext, Future}
import scala.util.matching.Regex
import scala.util.{Failure, Success}

class Recovery(
  descriptor: Recovery.Descriptor
)(implicit system: ActorSystem[SpawnProtocol.Command], parallelism: ParallelismConfig, providers: Providers)
    extends Operation { parent =>
  import Recovery._

  private implicit val mat: Materializer = SystemMaterializer(system).materializer

  private val supervision: Supervision.Decider = { e =>
    system.log.error("Recovery stream encountered failure: [{}: {}]; resuming", e.getClass.getSimpleName, e.getMessage, e)
    providers.track.failureEncountered(failure = e)
    Supervision.Resume
  }

  private implicit val ec: ExecutionContext = system.executionContext

  override implicit val id: Operation.Id = Operation.generateId()

  override val `type`: Operation.Type = Operation.Type.Recovery

  private val collector: RecoveryCollector = parent.descriptor.toRecoveryCollector()

  private val (killSwitch: UniqueKillSwitch, stream: Source[Done, NotUsed]) =
    stages.entityCollection
      .via(stages.entityProcessing)
      .via(stages.metadataApplication)
      .viaMat(KillSwitches.single)(Keep.right[NotUsed, UniqueKillSwitch])
      .withAttributes(ActorAttributes.supervisionStrategy(supervision))
      .preMaterialize()

  override def start(): Future[Done] =
    stream
      .runWith(Sink.ignore)
      .trackWith(providers.track)

  override def stop(): Unit =
    killSwitch.shutdown()

  private object stages extends EntityCollection with EntityProcessing with MetadataApplication {
    override protected lazy val deviceSecret: DeviceSecret = parent.descriptor.deviceSecret
    override protected lazy val providers: Providers = parent.providers
    override protected lazy val collector: RecoveryCollector = parent.collector
    override protected lazy val parallelism: ParallelismConfig = parent.parallelism
    override implicit protected lazy val mat: Materializer = parent.mat
    override implicit protected lazy val ec: ExecutionContext = parent.system.executionContext
  }
}

object Recovery {
  def apply(
    descriptor: Recovery.Descriptor
  )(implicit system: ActorSystem[SpawnProtocol.Command], parallelism: ParallelismConfig, providers: Providers): Recovery =
    new Recovery(descriptor)

  final case class Descriptor(
    targetMetadata: DatasetMetadata,
    query: Option[PathQuery],
    destination: Option[Destination],
    deviceSecret: DeviceSecret
  ) {
    def toRecoveryCollector()(implicit
      ec: ExecutionContext,
      mat: Materializer,
      parallelism: ParallelismConfig,
      providers: Providers
    ): RecoveryCollector =
      new RecoveryCollector.Default(
        targetMetadata = targetMetadata,
        keep = (entity, _) => query.forall(_.matches(entity.toAbsolutePath)),
        destination = destination.toTargetEntityDestination,
        metadataCollector = RecoveryMetadataCollector.Default(checksum = providers.checksum),
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
    )(implicit ec: ExecutionContext, providers: Providers): Future[Descriptor] =
      for {
        entry <- collector match {
          case Collector.WithDefinition(definition, until) =>
            providers.clients.api.latestEntry(definition, until).flatMap {
              case Some(entry) =>
                Future.successful(entry)

              case None =>
                Future.failed(
                  new IllegalStateException(
                    s"Expected dataset entry for definition [${definition.toString}] but none was found"
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
    keepStructure: Boolean,
    filesystem: FileSystem
  )

  object Destination {
    def apply(path: String, keepStructure: Boolean): Destination =
      Destination(path = path, keepStructure = keepStructure, filesystem = FileSystems.getDefault)
  }

  implicit class RecoveryToTargetEntityDestination(destination: Option[Destination]) {
    def toTargetEntityDestination: TargetEntity.Destination =
      destination match {
        case Some(destination) =>
          TargetEntity.Destination.Directory(
            path = destination.filesystem.getPath(destination.path),
            keepDefaultStructure = destination.keepStructure
          )

        case None =>
          TargetEntity.Destination.Default
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
