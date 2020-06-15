package stasis.client.model

import java.nio.file.Path

final case class TargetEntity(
  path: Path,
  destination: TargetEntity.Destination,
  existingMetadata: EntityMetadata,
  currentMetadata: Option[EntityMetadata]
) {
  currentMetadata.foreach(
    current =>
      require(
        current.getClass == existingMetadata.getClass,
        s"Mismatched current metadata for [${current.path.toString}] and existing metadata for [${existingMetadata.path.toString}]"
    )
  )

  def hasChanged: Boolean =
    currentMetadata match {
      case Some(current) => existingMetadata != current
      case None          => true
    }

  def hasContentChanged: Boolean =
    (existingMetadata, currentMetadata) match {
      case (existing: EntityMetadata.File, Some(current: EntityMetadata.File)) =>
        existing.size != current.size || existing.checksum != current.checksum

      case (_: EntityMetadata.File, None) =>
        true

      case _ =>
        false
    }

  val originalPath: Path = existingMetadata.path

  val destinationPath: Path =
    destination match {
      case TargetEntity.Destination.Default =>
        originalPath

      case TargetEntity.Destination.Directory(path, true) =>
        path.resolve(originalPath.getFileSystem.getPath("/").relativize(originalPath))

      case TargetEntity.Destination.Directory(path, false) =>
        path.resolve(originalPath.getFileName)
    }
}

object TargetEntity {
  sealed trait Destination
  object Destination {
    final case object Default extends Destination
    final case class Directory(path: Path, keepDefaultStructure: Boolean) extends Destination
  }
}
