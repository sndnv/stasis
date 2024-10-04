package stasis.client.ops.backup.stages

import java.nio.file.Files
import java.nio.file.Path

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source

import stasis.client.collection.rules.Rule
import stasis.client.collection.rules.Specification
import stasis.client.collection.BackupCollector
import stasis.client.collection.BackupMetadataCollector
import stasis.client.model.DatasetMetadata
import stasis.client.ops.ParallelismConfig
import stasis.client.ops.backup.Providers
import stasis.client.tracking.state.BackupState
import stasis.shared.ops.Operation

trait EntityDiscovery {
  protected def collector: EntityDiscovery.Collector
  protected def latestMetadata: Option[DatasetMetadata]
  protected def providers: Providers
  protected def parallelism: ParallelismConfig

  protected implicit def mat: Materializer
  protected implicit def ec: ExecutionContext

  def entityDiscovery(implicit operation: Operation.Id): Source[BackupCollector, NotUsed] = {
    val discovered = Source.lazyFuture(() =>
      collector match {
        case EntityDiscovery.Collector.WithRules(rules) =>
          Specification
            .tracked(rules, providers.track)
            .map { spec =>
              spec.includedParents.foreach(providers.track.entityDiscovered)
              providers.track.specificationProcessed(unmatched = spec.unmatched)
              spec.included
            }

        case EntityDiscovery.Collector.WithEntities(entities) =>
          val existing = entities.filter(entity => Files.exists(entity))
          existing.foreach(providers.track.entityDiscovered)
          Future.successful(existing)

        case EntityDiscovery.Collector.WithState(state) =>
          Future.successful(state.remainingEntities())
      }
    )

    discovered
      .map { entities =>
        new BackupCollector.Default(
          entities = entities.toList,
          latestMetadata = latestMetadata,
          metadataCollector = BackupMetadataCollector.Default(
            checksum = providers.checksum,
            compression = providers.compression
          ),
          api = providers.clients.api
        )(ec, parallelism)
      }
  }
}

object EntityDiscovery {
  sealed trait Collector
  object Collector {
    final case class WithRules(rules: Seq[Rule]) extends Collector
    final case class WithEntities(entities: Seq[Path]) extends Collector
    final case class WithState(state: BackupState) extends Collector
  }
}
