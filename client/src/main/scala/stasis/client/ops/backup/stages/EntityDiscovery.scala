package stasis.client.ops.backup.stages

import java.nio.file.Path

import scala.concurrent.ExecutionContext

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source

import stasis.client.collection.BackupCollector
import stasis.client.collection.rules.Rule
import stasis.client.collection.rules.SourceUri
import stasis.client.collection.rules.exceptions.RuleParsingFailure
import stasis.client.model.DatasetMetadata
import stasis.client.ops.ParallelismConfig
import stasis.client.ops.backup.BackupEntityKind
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
    reportUnsupportedSources()

    Source(providers.kinds.toList)
      .mapAsync(parallelism = 1)(_.collector(collector, latestMetadata, providers, parallelism))
  }

  private def reportUnsupportedSources()(implicit operation: Operation.Id): Unit =
    collector match {
      case EntityDiscovery.Collector.WithRules(rules) =>
        val handledSchemes: Set[Option[String]] = providers.kinds.collect {
          case _: BackupEntityKind.Filesystem => None
          case kind: BackupEntityKind.Library => Some(kind.scheme)
        }.toSet

        rules.foreach { rule =>
          if (!handledSchemes.contains(SourceUri.scheme(rule.source))) {
            providers.track.failureEncountered(
              new RuleParsingFailure(s"No backup kind was registered for source [${rule.source}]")
            )
          }
        }

      case _ =>
        () // do nothing
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
