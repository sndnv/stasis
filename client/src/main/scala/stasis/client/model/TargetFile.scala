package stasis.client.model

import java.nio.file.Path

final case class TargetFile(
  path: Path,
  existingMetadata: FileMetadata,
  currentMetadata: Option[FileMetadata]
) {
  def hasChanged: Boolean =
    currentMetadata match {
      case Some(current) => existingMetadata != current
      case None          => true
    }

  def hasContentChanged: Boolean =
    currentMetadata match {
      case Some(current) => existingMetadata.size != current.size || existingMetadata.checksum != current.checksum
      case None          => true
    }
}
