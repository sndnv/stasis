package stasis.client.ops.backup

import java.nio.file.Path

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

import org.apache.pekko.Done
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.stream._
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source

import stasis.client.collection.rules.Rule
import stasis.client.encryption.secrets.DeviceSecret
import stasis.client.model.DatasetMetadata
import stasis.client.ops.ParallelismConfig
import stasis.client.ops.backup.Backup.Descriptor.Collector
import stasis.client.ops.backup.stages._
import stasis.client.ops.exceptions.EntityProcessingFailure
import stasis.client.ops.exceptions.OperationStopped
import stasis.client.tracking.BackupTracker
import stasis.client.tracking.state.BackupState
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.datasets.DatasetEntry
import stasis.shared.ops.Operation

class Backup(
  descriptor: Backup.Descriptor
)(implicit system: ActorSystem[Nothing], parallelism: ParallelismConfig, providers: Providers)
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

  override implicit val id: Operation.Id = descriptor.collector.existingState
    .map(_.operation)
    .getOrElse(Operation.generateId())

  override val `type`: Operation.Type = Operation.Type.Backup

  private implicit val killSwitch: SharedKillSwitch = KillSwitches.shared("backup-kill-switch")

  private val stream: Source[Done, NotUsed] =
    stages.entityDiscovery
      .via(stages.entityCollection)
      .via(stages.entityProcessing)
      .via(stages.metadataCollection(descriptor.collector.existingState))
      .via(stages.metadataPush)
      .via(killSwitch.flow)
      .withAttributes(ActorAttributes.supervisionStrategy(supervision))

  override def start(): Future[Done] = {
    descriptor.collector match {
      case Collector.WithState(_) => () // do nothing, state already exists
      case _                      => providers.track.started(definition = descriptor.targetDataset.id)
    }

    stream
      .runWith(Sink.ignore)
      .trackWith(providers.track)
  }

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
  )(implicit system: ActorSystem[Nothing], parallelism: ParallelismConfig, providers: Providers): Backup =
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
      def existingState: Option[BackupState]
    }

    object Collector {
      final case class WithRules(rules: Seq[Rule]) extends Collector {
        override def asDiscoveryCollector: EntityDiscovery.Collector =
          EntityDiscovery.Collector.WithRules(rules)

        override def existingState: Option[BackupState] = None
      }

      final case class WithEntities(entities: Seq[Path]) extends Collector {
        override def asDiscoveryCollector: EntityDiscovery.Collector =
          EntityDiscovery.Collector.WithEntities(entities)

        override def existingState: Option[BackupState] = None
      }

      final case class WithState(state: BackupState) extends Collector {
        override def asDiscoveryCollector: EntityDiscovery.Collector =
          EntityDiscovery.Collector.WithState(state)

        override def existingState: Option[BackupState] = Some(state)
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
        case Success(_) =>
          track.completed()

        case Failure(_: OperationStopped) =>
          () // do nothing

        case Failure(e: EntityProcessingFailure) =>
          track.failureEncountered(entity = e.entity, failure = e.cause)

        case Failure(e) =>
          track.failureEncountered(failure = e)
      }

      operation
    }
  }
}
