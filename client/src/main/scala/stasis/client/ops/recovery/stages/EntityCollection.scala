package stasis.client.ops.recovery.stages

import akka.NotUsed
import akka.stream.scaladsl.Source
import stasis.client.collection.RecoveryCollector
import stasis.client.model.TargetEntity
import stasis.client.ops.recovery.Providers
import stasis.shared.ops.Operation

trait EntityCollection {
  protected def collector: RecoveryCollector
  protected def providers: Providers

  def entityCollection(implicit operation: Operation.Id): Source[TargetEntity, NotUsed] =
    collector
      .collect()
      .wireTap(entity =>
        providers.track.entityExamined(
          entity = entity.path,
          metadataChanged = entity.hasChanged,
          contentChanged = entity.hasContentChanged
        )
      )
      .filter(_.hasChanged)
      .wireTap(entity => providers.track.entityCollected(entity = entity.path))
}
