package stasis.client.ops.backup.stages

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import stasis.client.collection.rules.{Rule, Specification}
import stasis.client.collection.{BackupCollector, BackupMetadataCollector}
import stasis.client.model.DatasetMetadata
import stasis.client.ops.ParallelismConfig
import stasis.client.ops.backup.Providers
import stasis.shared.ops.Operation

import java.nio.file.{Files, Path}

import scala.concurrent.ExecutionContext

trait EntityDiscovery {
  protected def collector: EntityDiscovery.Collector
  protected def latestMetadata: Option[DatasetMetadata]
  protected def providers: Providers
  protected def parallelism: ParallelismConfig

  protected implicit def mat: Materializer
  protected implicit def ec: ExecutionContext

  def entityDiscovery(implicit operation: Operation.Id): Source[BackupCollector, NotUsed] = {
    val discovered = collector match {
      case EntityDiscovery.Collector.WithRules(rules) =>
        Source
          .future(Specification.tracked(rules, providers.track))
          .map { spec =>
            spec.includedParents.foreach(providers.track.entityDiscovered)
            providers.track.specificationProcessed(unmatched = spec.unmatched)
            spec.included.toList
          }

      case EntityDiscovery.Collector.WithEntities(entities) =>
        Source
          .single(entities.toList)
          .map { entities =>
            val existing = entities.filter(entity => Files.exists(entity))
            existing.foreach(providers.track.entityDiscovered)
            existing
          }
    }

    discovered
      .map { entities =>
        new BackupCollector.Default(
          entities = entities,
          latestMetadata = latestMetadata,
          metadataCollector = BackupMetadataCollector.Default(checksum = providers.checksum),
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
  }
}
