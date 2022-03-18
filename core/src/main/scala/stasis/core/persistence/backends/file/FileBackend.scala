package stasis.core.persistence.backends.file

import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import stasis.core.persistence.backends.StreamingBackend

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

class FileBackend(val parentDirectory: String)(implicit ec: ExecutionContext) extends StreamingBackend {

  private val parent: Path = Paths.get(parentDirectory)

  override val info: String = s"FileBackend(parentDirectory=$parentDirectory)"

  override def init(): Future[Done] =
    Future {
      val _ = Files.createDirectories(parent)
      Done
    }

  override def drop(): Future[Done] =
    if (Files.exists(parent)) {
      Future {
        parent.toFile.listFiles().foreach(_.delete)
        Files.delete(parent)
        Done
      }
    } else {
      Future.successful(Done)
    }

  override def available(): Future[Boolean] =
    Future {
      Files.exists(parent)
    }

  override def sink(key: UUID): Future[Sink[ByteString, Future[Done]]] =
    Future.successful(
      FileIO
        .toPath(key.toPath)
        .mapMaterializedValue(_.flatMap(_ => Future.successful(Done)))
    )

  override def source(key: UUID): Future[Option[Source[ByteString, NotUsed]]] = {
    val path = key.toPath
    Future.successful(
      if (Files.exists(path)) {
        Some(FileIO.fromPath(path).mapMaterializedValue(_ => NotUsed))
      } else {
        None
      }
    )
  }

  override def delete(key: UUID): Future[Boolean] =
    Future {
      Files.deleteIfExists(key.toPath)
    }

  override def contains(key: UUID): Future[Boolean] =
    Future {
      Files.exists(key.toPath)
    }

  override def canStore(bytes: Long): Future[Boolean] =
    Future {
      parent.getFileSystem.getFileStores.asScala.exists(_.getUsableSpace >= bytes)
    }

  private implicit class FileKey(key: UUID) {
    def toPath: Path = parent.resolve(key.toString)
  }
}
