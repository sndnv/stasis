package stasis.test.specs.unit.client.mocks

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.Future

import stasis.client.collection.BackupMetadataCollector
import stasis.client.model.EntityMetadata
import stasis.client.model.SourceEntity
import stasis.test.specs.unit.client.mocks.MockBackupMetadataCollector.Statistic

class MockBackupMetadataCollector(metadata: Map[Path, EntityMetadata]) extends BackupMetadataCollector {
  private val stats: Map[Statistic, AtomicInteger] = Map(
    Statistic.FileCollected -> new AtomicInteger(0)
  )

  override def collect(file: Path, existingMetadata: Option[EntityMetadata]): Future[SourceEntity] = {
    stats(Statistic.FileCollected).incrementAndGet()
    metadata.get(file) match {
      case Some(fileMetadata) =>
        Future.successful(SourceEntity(path = file, existingMetadata = None, currentMetadata = fileMetadata))

      case None =>
        Future.failed(new IllegalArgumentException(s"No metadata found for file [$file]"))
    }
  }

  def statistics: Map[Statistic, Int] = stats.view.mapValues(_.get()).toMap
}

object MockBackupMetadataCollector {
  sealed trait Statistic
  object Statistic {
    case object FileCollected extends Statistic
  }
}
