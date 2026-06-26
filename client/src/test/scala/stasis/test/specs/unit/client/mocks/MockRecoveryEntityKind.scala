package stasis.test.specs.unit.client.mocks

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

import stasis.client.collection.RecoveryCollector
import stasis.client.model.DatasetMetadata
import stasis.client.model.EntityRef
import stasis.client.model.FilesystemMetadata
import stasis.client.model.TargetEntity
import stasis.client.ops.ParallelismConfig
import stasis.client.ops.recovery.Providers
import stasis.client.ops.recovery.RecoveryEntityKind

class MockRecoveryEntityKind(recoveryCollector: RecoveryCollector) extends RecoveryEntityKind.Filesystem {
  override def collector(
    targetMetadata: DatasetMetadata,
    keep: (String, FilesystemMetadata.EntityState) => Boolean,
    destination: TargetEntity.Destination,
    providers: Providers,
    parallelism: ParallelismConfig
  )(implicit mat: Materializer): RecoveryCollector = recoveryCollector

  override def prepare(entity: TargetEntity, ref: EntityRef.Filesystem, providers: Providers): Unit =
    RecoveryEntityKind.Filesystem.prepare(entity, ref, providers)

  override def write(entity: TargetEntity, ref: EntityRef.Filesystem, content: Source[ByteString, NotUsed], providers: Providers)(
    implicit mat: Materializer
  ): Future[Done] =
    RecoveryEntityKind.Filesystem.write(entity, ref, content, providers)

  override def applyMetadata(entity: TargetEntity, ref: EntityRef.Filesystem, providers: Providers)(implicit
    ec: ExecutionContext
  ): Future[Done] =
    RecoveryEntityKind.Filesystem.applyMetadata(entity, ref, providers)
}
