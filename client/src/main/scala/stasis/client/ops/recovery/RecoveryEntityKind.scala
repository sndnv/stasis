package stasis.client.ops.recovery

import java.nio.file.Files

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

import stasis.client.analysis.Metadata
import stasis.client.analysis.PlatformMetadata
import stasis.client.collection.RecoveryCollector
import stasis.client.collection.RecoveryMetadataCollector
import stasis.client.collection.rules.SourceUri
import stasis.client.model.DatasetMetadata
import stasis.client.model.EntityMetadata
import stasis.client.model.EntityRef
import stasis.client.model.FilesystemMetadata
import stasis.client.model.TargetEntity
import stasis.client.ops.ParallelismConfig
import stasis.client.ops.recovery.stages.internal.DestagedByteStringSource

sealed trait RecoveryEntityKind {
  def collector(
    targetMetadata: DatasetMetadata,
    keep: (String, FilesystemMetadata.EntityState) => Boolean,
    destination: TargetEntity.Destination,
    providers: Providers,
    parallelism: ParallelismConfig
  )(implicit mat: Materializer): RecoveryCollector
}

object RecoveryEntityKind {
  trait Filesystem extends RecoveryEntityKind {
    def prepare(entity: TargetEntity, ref: EntityRef.Filesystem, providers: Providers): Unit

    def write(entity: TargetEntity, ref: EntityRef.Filesystem, content: Source[ByteString, NotUsed], providers: Providers)(
      implicit mat: Materializer
    ): Future[Done]

    def applyMetadata(entity: TargetEntity, ref: EntityRef.Filesystem, providers: Providers)(implicit
      ec: ExecutionContext
    ): Future[Done]
  }

  trait Library extends RecoveryEntityKind {
    def scheme: String

    def prepare(entity: TargetEntity, ref: EntityRef.Library, providers: Providers): Unit

    def write(entity: TargetEntity, ref: EntityRef.Library, content: Source[ByteString, NotUsed], providers: Providers)(implicit
      mat: Materializer
    ): Future[Done]

    def applyMetadata(entity: TargetEntity, ref: EntityRef.Library, providers: Providers)(implicit
      ec: ExecutionContext
    ): Future[Done]
  }

  def prepare(kinds: Seq[RecoveryEntityKind], entity: TargetEntity, providers: Providers): Unit =
    entity.destinationRef match {
      case ref: EntityRef.Filesystem =>
        kinds.collectFirst { case kind: Filesystem => kind.prepare(entity, ref, providers) }.getOrElse(())

      case ref: EntityRef.Library =>
        kinds
          .collectFirst { case kind: Library if kind.scheme == ref.scheme => kind.prepare(entity, ref, providers) }
          .getOrElse(())
    }

  def write(kinds: Seq[RecoveryEntityKind], entity: TargetEntity, content: Source[ByteString, NotUsed], providers: Providers)(
    implicit mat: Materializer
  ): Future[Done] =
    entity.destinationRef match {
      case ref: EntityRef.Filesystem =>
        kinds
          .collectFirst { case kind: Filesystem => kind.write(entity, ref, content, providers) }
          .getOrElse(Future.failed(new IllegalArgumentException("No filesystem recovery kind was registered")))

      case ref: EntityRef.Library =>
        kinds
          .collectFirst { case kind: Library if kind.scheme == ref.scheme => kind.write(entity, ref, content, providers) }
          .getOrElse(Future.failed(new IllegalArgumentException(s"No recovery kind was registered for scheme [${ref.scheme}]")))
    }

  def applyMetadata(kinds: Seq[RecoveryEntityKind], entity: TargetEntity, providers: Providers)(implicit
    ec: ExecutionContext
  ): Future[Done] =
    entity.destinationRef match {
      case ref: EntityRef.Filesystem =>
        kinds
          .collectFirst { case kind: Filesystem => kind.applyMetadata(entity, ref, providers) }
          .getOrElse(Future.failed(new IllegalArgumentException("No filesystem recovery kind was registered")))

      case ref: EntityRef.Library =>
        kinds
          .collectFirst { case kind: Library if kind.scheme == ref.scheme => kind.applyMetadata(entity, ref, providers) }
          .getOrElse(Future.failed(new IllegalArgumentException(s"No recovery kind was registered for scheme [${ref.scheme}]")))
    }

  object Filesystem extends Filesystem {
    override def collector(
      targetMetadata: DatasetMetadata,
      keep: (String, FilesystemMetadata.EntityState) => Boolean,
      destination: TargetEntity.Destination,
      providers: Providers,
      parallelism: ParallelismConfig
    )(implicit mat: Materializer): RecoveryCollector = {
      implicit val ec: ExecutionContext = mat.executionContext
      implicit val p: ParallelismConfig = parallelism

      new RecoveryCollector.Filesystem(
        targetMetadata = targetMetadata,
        keep = (entity, state) => keep(entity, state) && SourceUri.scheme(entity).isEmpty,
        destination = destination,
        metadataCollector = RecoveryMetadataCollector.Filesystem(checksum = providers.checksum),
        clients = providers.clients
      )
    }

    override def prepare(entity: TargetEntity, ref: EntityRef.Filesystem, providers: Providers): Unit = {
      val directory = entity.existingMetadata match {
        case _: EntityMetadata.Directory   => ref.path
        case _: EntityMetadata.WithContent => ref.path.getParent
      }

      val _ = Files.createDirectories(
        directory,
        PlatformMetadata.forFileSystem(directory.getFileSystem).ownerOnlyDirectoryAttributes: _*
      )
    }

    override def write(
      entity: TargetEntity,
      ref: EntityRef.Filesystem,
      content: Source[ByteString, NotUsed],
      providers: Providers
    )(implicit mat: Materializer): Future[Done] = {
      implicit val prv: Providers = providers
      new DestagedByteStringSource(content).destage(to = ref.path)
    }

    override def applyMetadata(entity: TargetEntity, ref: EntityRef.Filesystem, providers: Providers)(implicit
      ec: ExecutionContext
    ): Future[Done] =
      Metadata.applyEntityMetadataTo(metadata = entity.existingMetadata, entity = ref.path)(ec, providers.metadataDefaults)
  }
}
