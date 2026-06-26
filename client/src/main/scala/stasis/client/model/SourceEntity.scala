package stasis.client.model

final case class SourceEntity(
  ref: EntityRef,
  existingMetadata: Option[EntityMetadata],
  currentMetadata: EntityMetadata
) {
  existingMetadata.foreach(existing =>
    require(
      existing.getClass == currentMetadata.getClass,
      s"Mismatched current metadata for [${currentMetadata.path}] and existing metadata for [${existing.path}]"
    )
  )

  lazy val hasChanged: Boolean =
    existingMetadata match {
      case Some(existing) => existing.hasChanged(comparedTo = currentMetadata)
      case None           => true
    }

  lazy val hasContentChanged: Boolean =
    (existingMetadata, currentMetadata) match {
      case (Some(existing: EntityMetadata.WithContent), current: EntityMetadata.WithContent) =>
        existing.size != current.size || existing.checksum != current.checksum

      case (None, _: EntityMetadata.WithContent) =>
        true

      case _ =>
        false
    }
}
