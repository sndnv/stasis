package stasis.client.model

import scala.util.Try

final case class DatasetMetadata(contentChanged: Seq[FileMetadata], metadataChanged: Seq[FileMetadata])

object DatasetMetadata {
  def empty: DatasetMetadata = DatasetMetadata(contentChanged = Seq.empty, metadataChanged = Seq.empty)

  def toByteString(metadata: DatasetMetadata): akka.util.ByteString = {
    val data = proto.metadata.DatasetMetadata(
      contentChanged = metadata.contentChanged.map(FileMetadata.toProto),
      metadataChanged = metadata.metadataChanged.map(FileMetadata.toProto)
    )

    akka.util.ByteString(data.toByteArray)
  }

  def fromByteString(bytes: akka.util.ByteString): Try[DatasetMetadata] =
    Try {
      proto.metadata.DatasetMetadata.parseFrom(bytes.toArray)
    }.flatMap { data =>
      for {
        contentChanged <- foldTry(data.contentChanged.map(FileMetadata.fromProto))
        metadataChanged <- foldTry(data.metadataChanged.map(FileMetadata.fromProto))
      } yield {
        DatasetMetadata(
          contentChanged = contentChanged.toSeq,
          metadataChanged = metadataChanged.toSeq
        )
      }
    }

  private def foldTry[T](source: Iterable[Try[T]]): Try[Iterable[T]] =
    source.foldLeft(Try(Seq.empty[T])) {
      case (tryCollected, tryCurrent) =>
        tryCollected.flatMap { collected =>
          tryCurrent.map(current => collected :+ current)
        }
    }
}
