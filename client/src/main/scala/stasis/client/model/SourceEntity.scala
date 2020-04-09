package stasis.client.model

import java.nio.file.Path

final case class SourceEntity(
  path: Path,
  existingMetadata: Option[EntityMetadata],
  currentMetadata: EntityMetadata
) {
  existingMetadata.foreach(
    existing =>
      require(
        existing.getClass == currentMetadata.getClass,
        s"Mismatched current metadata for [${currentMetadata.path}] and existing metadata for [${existing.path}]"
    )
  )

  def hasChanged: Boolean =
    existingMetadata match {
      case Some(existing) => existing != currentMetadata
      case None           => true
    }

  def hasContentChanged: Boolean =
    (existingMetadata, currentMetadata) match {
      case (Some(existing: EntityMetadata.File), current: EntityMetadata.File) =>
        existing.size != current.size || existing.checksum != current.checksum

      case (None, _: EntityMetadata.File) =>
        true

      case _ =>
        false
    }
}
