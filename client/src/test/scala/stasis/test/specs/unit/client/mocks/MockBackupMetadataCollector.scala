package stasis.test.specs.unit.client.mocks

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

import stasis.client.collection.BackupMetadataCollector
import stasis.client.model.{FileMetadata, SourceFile}
import stasis.test.specs.unit.client.mocks.MockBackupMetadataCollector.Statistic

import scala.concurrent.Future

class MockBackupMetadataCollector(metadata: Map[Path, FileMetadata]) extends BackupMetadataCollector {
  private val stats: Map[Statistic, AtomicInteger] = Map(
    Statistic.FileCollected -> new AtomicInteger(0),
  )

  override def collect(file: Path, existingMetadata: Option[FileMetadata]): Future[SourceFile] = {
    stats(Statistic.FileCollected).incrementAndGet()
    metadata.get(file) match {
      case Some(fileMetadata) =>
        Future.successful(SourceFile(path = file, existingMetadata = None, currentMetadata = fileMetadata))

      case None =>
        Future.failed(new IllegalArgumentException(s"No metadata found for file [$file]"))
    }
  }

  def statistics: Map[Statistic, Int] = stats.mapValues(_.get())
}

object MockBackupMetadataCollector {
  sealed trait Statistic
  object Statistic {
    case object FileCollected extends Statistic
  }
}
