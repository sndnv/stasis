package stasis.test.specs.unit.client.mocks

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.Future

import stasis.client.collection.RecoveryMetadataCollector
import stasis.client.model.EntityMetadata
import stasis.client.model.TargetEntity
import stasis.test.specs.unit.client.mocks.MockRecoveryMetadataCollector.Statistic

class MockRecoveryMetadataCollector(metadata: Map[Path, EntityMetadata]) extends RecoveryMetadataCollector {
  private val stats: Map[Statistic, AtomicInteger] = Map(
    Statistic.FileCollectedWithExistingMetadata -> new AtomicInteger(0)
  )

  override def collect(
    file: Path,
    destination: TargetEntity.Destination,
    existingMetadata: EntityMetadata
  ): Future[TargetEntity] = {
    stats(Statistic.FileCollectedWithExistingMetadata).incrementAndGet()
    metadata.get(file) match {
      case Some(fileMetadata) =>
        Future.successful(
          TargetEntity(
            path = file,
            destination = destination,
            existingMetadata = existingMetadata,
            currentMetadata = Some(fileMetadata)
          )
        )

      case None =>
        Future.failed(new IllegalArgumentException(s"No metadata found for file [$file]"))
    }
  }

  def statistics: Map[Statistic, Int] = stats.view.mapValues(_.get()).toMap
}

object MockRecoveryMetadataCollector {
  sealed trait Statistic
  object Statistic {
    case object FileCollectedWithExistingMetadata extends Statistic
  }
}
