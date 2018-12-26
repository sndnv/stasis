package stasis.persistence.backends.file

import java.nio.file.{Files, Path, Paths}

import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import stasis.persistence.backends.StreamingBackend

import scala.concurrent.{ExecutionContext, Future}

class FileBackend[K <: java.util.UUID](parentDirectory: String)(implicit ec: ExecutionContext)
    extends StreamingBackend[K] {

  private val parent: Path = Paths.get(parentDirectory)

  override def init(): Future[Done] = Future {
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

  override def sink(key: K): Future[Sink[ByteString, Future[Done]]] =
    Future.successful(
      FileIO
        .toPath(key.toPath)
        .mapMaterializedValue(_.flatMap(_ => Future.successful(Done)))
    )

  override def source(key: K): Future[Option[Source[ByteString, NotUsed]]] = {
    val path = key.toPath
    Future.successful(
      if (Files.exists(path)) {
        Some(FileIO.fromPath(path).mapMaterializedValue(_ => NotUsed))
      } else {
        None
      }
    )
  }

  override def delete(key: K): Future[Boolean] = Future {
    Files.deleteIfExists(key.toPath)
  }

  override def exists(key: K): Future[Boolean] = Future {
    Files.exists(key.toPath)
  }

  private implicit class FileKey(key: K) {
    def toPath: Path = parent.resolve(key.toString)
  }
}
