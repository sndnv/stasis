package stasis.test.specs.unit.client.ops.integration.mocks

import java.nio.file.FileSystem

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

import stasis.client.collection.RecoveryCollector
import stasis.client.collection.rules.SourceUri
import stasis.client.model.DatasetMetadata
import stasis.client.model.EntityMetadata
import stasis.client.model.EntityRef
import stasis.client.model.FilesystemMetadata
import stasis.client.model.TargetEntity
import stasis.client.ops.ParallelismConfig
import stasis.client.ops.recovery.RecoveryEntityKind
import stasis.client.ops.recovery.{Providers => RecoveryProviders}

class MockRecoveryLibraryKind(library: MockLibrary) extends RecoveryEntityKind.Library {
  override def scheme: String = library.scheme

  override def collector(
    targetMetadata: DatasetMetadata,
    keep: (String, FilesystemMetadata.EntityState) => Boolean,
    destination: TargetEntity.Destination,
    providers: RecoveryProviders,
    parallelism: ParallelismConfig
  )(implicit mat: Materializer): RecoveryCollector =
    (_: FileSystem) => {
      implicit val ec: ExecutionContext = mat.executionContext

      val entities = targetMetadata.filesystem.collect {
        case (entity, state) if SourceUri.scheme(entity).contains(scheme) && keep(entity, state) =>
          targetMetadata.require(entity = entity, clients = providers.clients).map { metadata =>
            TargetEntity(
              ref = EntityRef.default(entity),
              destination = destination,
              existingMetadata = metadata,
              currentMetadata = None
            )
          }
      }.toList

      Source(entities).mapAsync(parallelism.entities)(identity)
    }

  override def prepare(entity: TargetEntity, ref: EntityRef.Library, providers: RecoveryProviders): Unit =
    ()

  override def write(
    entity: TargetEntity,
    ref: EntityRef.Library,
    content: Source[ByteString, NotUsed],
    providers: RecoveryProviders
  )(implicit mat: Materializer): Future[Done] = {
    implicit val ec: ExecutionContext = mat.executionContext
    content.runFold(ByteString.empty)(_ concat _).map { bytes =>
      val _ = library.restored.put(ref.key, bytes)
      Done
    }
  }

  override def applyMetadata(entity: TargetEntity, ref: EntityRef.Library, providers: RecoveryProviders)(implicit
    ec: ExecutionContext
  ): Future[Done] = {
    entity.existingMetadata match {
      case metadata: EntityMetadata.Library => val _ = library.restoredAttributes.put(ref.key, metadata.attributes)
      case _                                => ()
    }
    Future.successful(Done)
  }
}
