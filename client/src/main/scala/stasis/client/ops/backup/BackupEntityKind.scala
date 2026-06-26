package stasis.client.ops.backup

import java.nio.file.Files
import java.nio.file.Path

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.FileIO
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

import stasis.client.collection.BackupCollector
import stasis.client.collection.BackupMetadataCollector
import stasis.client.collection.rules.SourceUri
import stasis.client.collection.rules.Specification
import stasis.client.model.DatasetMetadata
import stasis.client.model.EntityRef
import stasis.client.model.SourceEntity
import stasis.client.ops.ParallelismConfig
import stasis.client.ops.backup.stages.EntityDiscovery
import stasis.shared.ops.Operation

sealed trait BackupEntityKind {
  def collector(
    collector: EntityDiscovery.Collector,
    latestMetadata: Option[DatasetMetadata],
    providers: Providers,
    parallelism: ParallelismConfig
  )(implicit operation: Operation.Id, mat: Materializer): Future[BackupCollector]
}

object BackupEntityKind {
  trait Filesystem extends BackupEntityKind {
    def read(entity: SourceEntity, ref: EntityRef.Filesystem, chunkSize: Int): Source[ByteString, NotUsed]
  }

  trait Library extends BackupEntityKind {
    def scheme: String

    def read(entity: SourceEntity, ref: EntityRef.Library, chunkSize: Int): Source[ByteString, NotUsed]
  }

  def read(kinds: Seq[BackupEntityKind], entity: SourceEntity, chunkSize: Int): Source[ByteString, NotUsed] =
    entity.ref match {
      case ref: EntityRef.Filesystem =>
        kinds
          .collectFirst { case kind: Filesystem => kind.read(entity, ref, chunkSize) }
          .getOrElse(Source.failed(new IllegalArgumentException("No filesystem backup kind was registered")))

      case ref: EntityRef.Library =>
        kinds
          .collectFirst { case kind: Library if kind.scheme == ref.scheme => kind.read(entity, ref, chunkSize) }
          .getOrElse(Source.failed(new IllegalArgumentException(s"No backup kind was registered for scheme [${ref.scheme}]")))
    }

  object Filesystem extends Filesystem {
    override def collector(
      collector: EntityDiscovery.Collector,
      latestMetadata: Option[DatasetMetadata],
      providers: Providers,
      parallelism: ParallelismConfig
    )(implicit operation: Operation.Id, mat: Materializer): Future[BackupCollector] = {
      implicit val ec: ExecutionContext = mat.executionContext
      implicit val p: ParallelismConfig = parallelism

      resolveEntities(collector, providers).map { entities =>
        new BackupCollector.Filesystem(
          entities = entities.toList,
          latestMetadata = latestMetadata,
          metadataCollector = BackupMetadataCollector.Filesystem(
            checksum = providers.checksum,
            compression = providers.compression
          ),
          clients = providers.clients
        )
      }
    }

    override def read(entity: SourceEntity, ref: EntityRef.Filesystem, chunkSize: Int): Source[ByteString, NotUsed] =
      FileIO.fromPath(f = ref.path, chunkSize = chunkSize).mapMaterializedValue(_ => NotUsed)

    private def resolveEntities(
      collector: EntityDiscovery.Collector,
      providers: Providers
    )(implicit operation: Operation.Id, ec: ExecutionContext): Future[Seq[Path]] =
      collector match {
        case EntityDiscovery.Collector.WithRules(rules) =>
          Specification
            .apply(
              rules = rules.filter(rule => SourceUri.scheme(rule.source).isEmpty),
              onMatchIncluded = path => providers.track.entityDiscovered(EntityRef.Filesystem(path)),
              filesystem = providers.filesystem
            )
            .map { spec =>
              spec.includedParents.foreach(parent => providers.track.entityDiscovered(EntityRef.Filesystem(parent)))
              providers.track.specificationProcessed(unmatched = spec.unmatched)
              spec.included
            }

        case EntityDiscovery.Collector.WithEntities(entities) =>
          val existing = entities.filter(entity => Files.exists(entity))
          existing.foreach(entity => providers.track.entityDiscovered(EntityRef.Filesystem(entity)))
          Future.successful(existing)

        case EntityDiscovery.Collector.WithState(state) =>
          Future.successful(
            state.remainingEntities().collect { case ref: EntityRef.Filesystem =>
              providers.filesystem.getPath(ref.key)
            }
          )
      }
  }
}
