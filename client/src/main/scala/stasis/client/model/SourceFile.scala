package stasis.client.model

import java.nio.file.Path

final case class SourceFile(path: Path, existingMetadata: Option[FileMetadata], currentMetadata: FileMetadata) {
  def hasChanged: Boolean =
    existingMetadata match {
      case Some(existing) => existing != currentMetadata
      case None           => true
    }

  def hasContentChanged: Boolean =
    existingMetadata match {
      case Some(existing) => existing.size != currentMetadata.size || existing.checksum != currentMetadata.checksum
      case None           => true
    }
}
