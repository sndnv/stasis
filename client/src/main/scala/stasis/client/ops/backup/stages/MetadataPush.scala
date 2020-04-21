package stasis.client.ops.backup.stages

import akka.{Done, NotUsed}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Source}
import stasis.client.encryption.secrets.DeviceSecret
import stasis.client.model.{DatasetMetadata, EntityMetadata}
import stasis.client.ops.backup.Providers
import stasis.client.ops.ParallelismConfig
import stasis.core.packaging.{Crate, Manifest}
import stasis.shared.api.requests.CreateDatasetEntry
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.shared.ops.Operation

import scala.concurrent.{ExecutionContext, Future}

trait MetadataPush {
  protected def targetDataset: DatasetDefinition
  protected def deviceSecret: DeviceSecret
  protected def providers: Providers
  protected def parallelism: ParallelismConfig

  protected implicit def mat: Materializer

  private implicit val ec: ExecutionContext = mat.executionContext

  def metadataPush(implicit operation: Operation.Id): Flow[DatasetMetadata, Done, NotUsed] =
    Flow[DatasetMetadata]
      .mapAsyncUnordered(parallelism.value)(pushMetadata)
      .wireTap(entry => providers.track.metadataPushed(entry))
      .map(_ => Done)

  private def pushMetadata(metadata: DatasetMetadata): Future[DatasetEntry.Id] = {
    val metadataCrate = Crate.generateId()

    DatasetMetadata
      .encrypt(
        metadataCrate = metadataCrate,
        metadataSecret = deviceSecret.toMetadataSecret(metadataCrate),
        metadata = metadata,
        encoder = providers.encryptor
      )
      .flatMap { encryptedMetadata =>
        val content = Source.single(encryptedMetadata)

        val metadataManifest: Manifest = Manifest(
          crate = metadataCrate,
          origin = providers.clients.core.self,
          source = providers.clients.core.self,
          size = encryptedMetadata.size,
          copies = targetDataset.redundantCopies
        )

        val request = CreateDatasetEntry(
          definition = targetDataset.id,
          device = providers.clients.api.self,
          data = metadata.contentChanged.values
            .collect { case metadata: EntityMetadata.File => metadata }
            .flatMap(_.crates.values)
            .toSet,
          metadata = metadataManifest.crate
        )

        for {
          _ <- providers.clients.core.push(metadataManifest, content)
          created <- providers.clients.api.createDatasetEntry(request)
        } yield {
          created.entry
        }
      }
  }
}
