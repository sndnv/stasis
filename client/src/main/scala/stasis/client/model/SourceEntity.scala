package stasis.client.model

import java.nio.file.Path

final case class SourceEntity(
  path: Path,
  existingMetadata: Option[EntityMetadata],
  currentMetadata: EntityMetadata
) {
  existingMetadata.foreach(existing =>
    require(
      existing.getClass == currentMetadata.getClass,
      s"Mismatched current metadata for [${currentMetadata.path.toString}] and existing metadata for [${existing.path.toString}]"
    )
  )

  lazy val hasChanged: Boolean =
    existingMetadata match {
      case Some(existing) => existing.hasChanged(comparedTo = currentMetadata)
      case None           => true
    }

  lazy val hasContentChanged: Boolean =
    (existingMetadata, currentMetadata) match {
      case (Some(existing: EntityMetadata.File), current: EntityMetadata.File) =>
        existing.size != current.size || existing.checksum != current.checksum

      case (None, _: EntityMetadata.File) =>
        true

      case _ =>
        false
    }
}
