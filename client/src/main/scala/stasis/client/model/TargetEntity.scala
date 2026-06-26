package stasis.client.model

import java.nio.file.Path

final case class TargetEntity(
  ref: EntityRef,
  destination: TargetEntity.Destination,
  existingMetadata: EntityMetadata,
  currentMetadata: Option[EntityMetadata]
) {
  currentMetadata.foreach(current =>
    require(
      current.getClass == existingMetadata.getClass,
      s"Mismatched current metadata for [${current.path}] and existing metadata for [${existingMetadata.path}]"
    )
  )

  lazy val hasChanged: Boolean =
    currentMetadata match {
      case Some(current) => existingMetadata.hasChanged(comparedTo = current)
      case None          => true
    }

  lazy val hasContentChanged: Boolean =
    (existingMetadata, currentMetadata) match {
      case (existing: EntityMetadata.WithContent, Some(current: EntityMetadata.WithContent)) =>
        existing.size != current.size || existing.checksum != current.checksum

      case (_: EntityMetadata.WithContent, None) =>
        true

      case _ =>
        false
    }

  val originalRef: EntityRef =
    ref.mapFilesystem(path => path.getFileSystem.getPath(existingMetadata.path))

  val destinationRef: EntityRef =
    destination match {
      case TargetEntity.Destination.Default =>
        originalRef

      case TargetEntity.Destination.Directory(path, keepDefaultStructure) =>
        originalRef.flatMap { ref =>
          val original = ref match {
            case fs: EntityRef.Filesystem   => fs.path
            case library: EntityRef.Library => path.getFileSystem.getPath(library.path)
          }

          EntityRef.Filesystem(
            if (keepDefaultStructure) {
              path.resolve(original.getFileSystem.getPath("/").relativize(original))
            } else {
              path.resolve(original.getFileName)
            }
          )
        }
    }
}

object TargetEntity {
  sealed trait Destination
  object Destination {
    final case object Default extends Destination
    final case class Directory(path: Path, keepDefaultStructure: Boolean) extends Destination
  }
}
