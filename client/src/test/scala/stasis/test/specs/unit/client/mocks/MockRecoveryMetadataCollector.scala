package stasis.test.specs.unit.client.mocks

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

import stasis.client.collection.RecoveryMetadataCollector
import stasis.client.model.{FileMetadata, SourceFile}
import stasis.test.specs.unit.client.mocks.MockRecoveryMetadataCollector.Statistic

import scala.concurrent.Future

class MockRecoveryMetadataCollector(metadata: Map[Path, FileMetadata]) extends RecoveryMetadataCollector {
  private val stats: Map[Statistic, AtomicInteger] = Map(
    Statistic.FileCollectedWithExistingMetadata -> new AtomicInteger(0)
  )

  override def collect(file: Path, existingMetadata: FileMetadata): Future[SourceFile] = {
    stats(Statistic.FileCollectedWithExistingMetadata).incrementAndGet()
    metadata.get(file) match {
      case Some(fileMetadata) =>
        Future.successful(
          SourceFile(
            path = file,
            existingMetadata = Some(existingMetadata),
            currentMetadata = fileMetadata
          )
        )

      case None =>
        Future.failed(new IllegalArgumentException(s"No metadata found for file [$file]"))
    }
  }

  def statistics: Map[Statistic, Int] = stats.mapValues(_.get())
}

object MockRecoveryMetadataCollector {
  sealed trait Statistic
  object Statistic {
    case object FileCollectedWithExistingMetadata extends Statistic
  }
}
