package stasis.test.specs.unit.client.ops.integration.mocks

import java.time.Instant

import scala.concurrent.Future

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

import stasis.client.collection.BackupCollector
import stasis.client.collection.rules.Rule
import stasis.client.collection.rules.SourceUri
import stasis.client.model.DatasetMetadata
import stasis.client.model.EntityMetadata
import stasis.client.model.EntityRef
import stasis.client.model.SourceEntity
import stasis.client.ops.ParallelismConfig
import stasis.client.ops.backup.BackupEntityKind
import stasis.client.ops.backup.stages.EntityDiscovery
import stasis.client.ops.backup.{Providers => BackupProviders}
import stasis.shared.ops.Operation

class MockBackupLibraryKind(library: MockLibrary) extends BackupEntityKind.Library {
  override def scheme: String = library.scheme

  override def collector(
    collector: EntityDiscovery.Collector,
    latestMetadata: Option[DatasetMetadata],
    providers: BackupProviders,
    parallelism: ParallelismConfig
  )(implicit operation: Operation.Id, mat: Materializer): Future[BackupCollector] = {
    val rules = collector match {
      case EntityDiscovery.Collector.WithRules(rules) => rules
      case _                                          => Seq.empty
    }

    val libraryRules = rules.filter(rule => SourceUri.scheme(rule.source).contains(scheme))
    val includes = libraryRules.collect { case rule if rule.operation == Rule.Operation.Include => rule.source }
    val excludes = libraryRules.collect { case rule if rule.operation == Rule.Operation.Exclude => rule.source }

    val sourceEntities = library.entries.toList.collect {
      case (key, content) if includes.exists(key.startsWith) && !excludes.exists(key.startsWith) =>
        SourceEntity(
          ref = EntityRef.default(key),
          existingMetadata = None,
          currentMetadata = libraryMetadata(key, content)
        )
    }

    Future.successful(() => Source(sourceEntities))
  }

  override def read(entity: SourceEntity, ref: EntityRef.Library, chunkSize: Int): Source[ByteString, NotUsed] =
    Source.single(library.entries(ref.key))

  private def libraryMetadata(key: String, content: ByteString): EntityMetadata.Library =
    EntityMetadata.Library(
      path = key,
      created = Instant.now(),
      updated = Instant.now(),
      size = content.length.toLong,
      checksum = BigInt(content.length),
      crates = Map.empty,
      compression = "deflate",
      attributes = ByteString(s"attributes:$key")
    )
}
