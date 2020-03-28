package stasis.client.model

import java.nio.file.{Path, Paths}

final case class TargetFile(
  path: Path,
  destination: TargetFile.Destination,
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

  val originalPath: Path = existingMetadata.path

  val destinationPath: Path =
    destination match {
      case TargetFile.Destination.Default                => originalPath
      case TargetFile.Destination.Directory(path, true)  => path.resolve(Paths.get("/").relativize(originalPath))
      case TargetFile.Destination.Directory(path, false) => path.resolve(originalPath.getFileName)
    }
}

object TargetFile {
  sealed trait Destination
  object Destination {
    final case object Default extends Destination
    final case class Directory(path: Path, keepDefaultStructure: Boolean) extends Destination
  }
}
