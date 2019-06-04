package stasis.client.ops.backup.stages

import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import stasis.client.encryption.secrets.DeviceSecret
import stasis.client.model.DatasetMetadata
import stasis.client.ops.ParallelismConfig
import stasis.client.ops.backup.{Clients, Providers}
import stasis.core.packaging.{Crate, Manifest}
import stasis.shared.api.requests.CreateDatasetEntry
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}

import scala.concurrent.{ExecutionContext, Future}

trait MetadataPush {
  protected def targetDataset: DatasetDefinition
  protected def deviceSecret: DeviceSecret
  protected def providers: Providers
  protected def clients: Clients
  protected def parallelism: ParallelismConfig

  protected implicit def mat: Materializer

  private implicit val ec: ExecutionContext = mat.executionContext

  def metadataPush: Flow[DatasetMetadata, DatasetEntry.Id, NotUsed] =
    Flow[DatasetMetadata]
      .mapAsyncUnordered(parallelism.value)(pushMetadata)
      .log(
        name = "Metadata Push",
        extract = entry => s"Metadata pushed for dataset [${targetDataset.id}]; entry created: [$entry]"
      )

  private def pushMetadata(metadata: DatasetMetadata): Future[DatasetEntry.Id] = {
    val metadataCrate = Crate.generateId()

    encryptMetadata(metadataCrate, metadata)
      .flatMap { encryptedMetadata =>
        val content = Source.single(encryptedMetadata)

        val metadataManifest: Manifest = Manifest(
          crate = metadataCrate,
          origin = clients.core.self,
          source = clients.core.self,
          size = encryptedMetadata.size,
          copies = targetDataset.redundantCopies
        )

        val request = CreateDatasetEntry(
          definition = targetDataset.id,
          device = clients.api.self,
          data = metadata.contentChanged.map(_.crate).toSet,
          metadata = metadataManifest.crate
        )

        for {
          _ <- clients.core.push(metadataManifest, content)
          entry <- clients.api.createDatasetEntry(request)
        } yield {
          entry
        }
      }
  }

  private def encryptMetadata(metadataCrate: Crate.Id, metadata: DatasetMetadata): Future[ByteString] =
    Source
      .single(metadata)
      .map(DatasetMetadata.toByteString)
      .via(providers.encryptor.encrypt(deviceSecret.toMetadataSecret(metadataCrate)))
      .runFold(ByteString.empty)(_ concat _)
}
