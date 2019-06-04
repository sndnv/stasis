package stasis.client.ops

import akka.Done
import akka.actor.ActorSystem
import akka.stream.scaladsl.Sink
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Materializer, Supervision}
import stasis.client.encryption.secrets.DeviceSecret
import stasis.client.ops.backup.stages.{FileCollection, FileProcessing, MetadataCollection, MetadataPush}
import stasis.client.ops.backup.{Clients, Providers}
import stasis.shared.model.datasets.DatasetDefinition

import scala.concurrent.{ExecutionContext, Future}

class Backup(
  targetDataset: DatasetDefinition,
  deviceSecret: DeviceSecret,
  providers: Providers,
  clients: Clients
)(implicit system: ActorSystem, parallelism: ParallelismConfig) { parent =>
  private implicit val ec: ExecutionContext = system.dispatcher

  private implicit val mat: Materializer = ActorMaterializer(
    ActorMaterializerSettings(system).withSupervisionStrategy { e =>
      system.log.error(e, "Backup stream encountered failure: [{}]; resuming", e.getMessage)
      Supervision.Resume
    }
  )

  def start(): Future[Done] =
    stages.fileCollection
      .via(stages.fileProcessing)
      .via(stages.metadataCollection)
      .via(stages.metadataPush)
      .runWith(Sink.ignore)

  private object stages extends FileCollection with FileProcessing with MetadataCollection with MetadataPush {
    override protected def targetDataset: DatasetDefinition = parent.targetDataset
    override protected def deviceSecret: DeviceSecret = parent.deviceSecret
    override protected def providers: Providers = parent.providers
    override protected def clients: Clients = parent.clients
    override protected def parallelism: ParallelismConfig = parent.parallelism
    override implicit protected def mat: Materializer = parent.mat
    override implicit protected def ec: ExecutionContext = parent.system.dispatcher
  }
}
