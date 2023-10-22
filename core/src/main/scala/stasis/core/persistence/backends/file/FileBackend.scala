package stasis.core.persistence.backends.file

import org.apache.pekko.actor.typed.{ActorSystem, DispatcherSelector, SpawnProtocol}
import org.apache.pekko.stream.scaladsl.{FileIO, Sink, Source}
import org.apache.pekko.util.ByteString
import org.apache.pekko.{Done, NotUsed}
import stasis.core.persistence.Metrics
import stasis.core.persistence.backends.StreamingBackend
import stasis.core.telemetry.TelemetryContext

import java.nio.file.{Files, Path, Paths}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class FileBackend(
  val parentDirectory: String
)(implicit system: ActorSystem[SpawnProtocol.Command], telemetry: TelemetryContext)
    extends StreamingBackend {
  private implicit val ec: ExecutionContext = system.dispatchers.lookup(DispatcherSelector.blocking())

  private val parent: Path = Paths.get(parentDirectory)
  private val metrics = telemetry.metrics[Metrics.StreamingBackend]

  override val info: String = s"FileBackend(parentDirectory=$parentDirectory)"

  override def init(): Future[Done] =
    Future {
      val _ = Files.createDirectories(parent)
      metrics.recordInit(backend = parentDirectory)
      Done
    }

  override def drop(): Future[Done] =
    if (Files.exists(parent)) {
      Future {
        parent.toFile.listFiles().foreach(_.delete)
        Files.delete(parent)
        metrics.recordDrop(backend = parentDirectory)
        Done
      }
    } else {
      metrics.recordDrop(backend = parentDirectory)
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
        .contramap { bytes: ByteString =>
          metrics.recordWrite(backend = parentDirectory, bytes = bytes.length.toLong)
          bytes
        }
        .mapMaterializedValue(_.flatMap(_ => Future.successful(Done)))
    )

  override def source(key: UUID): Future[Option[Source[ByteString, NotUsed]]] = {
    val path = key.toPath
    Future.successful(
      if (Files.exists(path)) {
        Some(
          FileIO
            .fromPath(path)
            .wireTap(bytes => metrics.recordRead(backend = parentDirectory, bytes = bytes.length.toLong))
            .mapMaterializedValue(_ => NotUsed)
        )
      } else {
        None
      }
    )
  }

  override def delete(key: UUID): Future[Boolean] =
    Future {
      val result = Files.deleteIfExists(key.toPath)
      metrics.recordDiscard(backend = parentDirectory)
      result
    }

  override def contains(key: UUID): Future[Boolean] =
    Future {
      Files.exists(key.toPath)
    }

  override def canStore(bytes: Long): Future[Boolean] =
    Future {
      Files.getFileStore(parent).getUsableSpace >= bytes
    }

  private implicit class FileKey(key: UUID) {
    def toPath: Path = parent.resolve(key.toString)
  }
}

object FileBackend {
  def apply(parentDirectory: String)(implicit
    system: ActorSystem[SpawnProtocol.Command],
    telemetry: TelemetryContext
  ): FileBackend = new FileBackend(parentDirectory = parentDirectory)
}
