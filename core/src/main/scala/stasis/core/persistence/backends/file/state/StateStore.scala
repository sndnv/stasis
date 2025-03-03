package stasis.core.persistence.backends.file.state

import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.ThreadLocalRandom

import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.util.Random
import scala.util.Try
import scala.util.control.NonFatal

import org.apache.pekko.Done
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.FileIO
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class StateStore[S](
  directory: String,
  retainedVersions: Int,
  filesystem: FileSystem
)(implicit mat: Materializer, serdes: StateStore.Serdes[S]) {
  import mat.executionContext

  private val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private val target = filesystem.getPath(directory)

  def persist(state: S): Future[Done] = {
    val serialized = serdes.serialize(state)
    val timestamp = Instant.now().toEpochMilli.toString
    val suffix = Random.javaRandomToRandom(ThreadLocalRandom.current()).alphanumeric.take(4).mkString

    val _ = Files.createDirectories(target)
    val path = target.resolve(s"state_${timestamp}_$suffix")

    Source
      .single(ByteString.fromArrayUnsafe(serialized))
      .runWith(FileIO.toPath(f = path))
      .flatMap(_ => prune(keep = retainedVersions))
  }

  def discard(): Future[Done] =
    prune(keep = 0)

  def prune(keep: Int): Future[Done] =
    collectStateFiles().map { stateFiles =>
      stateFiles.dropRight(keep).foreach(Files.delete)
      Done
    }

  def restore(): Future[Option[S]] = {
    def load(file: Path): Future[Option[S]] =
      FileIO
        .fromPath(f = file)
        .map(_.toArrayUnsafe())
        .runFold(Array.emptyByteArray)(_ ++ _)
        .flatMap { bytes =>
          log.debug(
            "Loaded [{}] bytes from file [{}]",
            bytes.length,
            file.toAbsolutePath.toString
          )

          Future.fromTry(serdes.deserialize(bytes))
        }
        .map(Option.apply)
        .recover { case NonFatal(e) =>
          log.error(
            "Failed to load state from file [{}]: [{} - {}]",
            file.toAbsolutePath.toString,
            e.getClass.getSimpleName,
            e.getMessage
          )

          None
        }

    for {
      stateFiles <- collectStateFiles()
      state <- Source(stateFiles.reverse)
        .mapAsync(parallelism = 1)(load)
        .collect { case Some(state) => state }
        .take(1)
        .runWith(Sink.headOption)
    } yield {
      state
    }
  }

  private def collectStateFiles(): Future[List[Path]] = Future {
    Files
      .walk(target)
      .filter { path =>
        !Files.isDirectory(path) && path.getFileName.toString.startsWith("state_")
      }
      .iterator()
      .asScala
      .toList
      .sorted
  }
}

object StateStore {
  final val MinRetainedVersions: Int = 2

  def apply[S](
    directory: String,
    retainedVersions: Int,
    filesystem: FileSystem
  )(implicit mat: Materializer, serdes: Serdes[S]): StateStore[S] =
    new StateStore(
      directory = directory,
      retainedVersions = retainedVersions,
      filesystem = filesystem
    )

  def apply[S](
    directory: String,
    filesystem: FileSystem
  )(implicit mat: Materializer, serdes: Serdes[S]): StateStore[S] =
    new StateStore(
      directory = directory,
      retainedVersions = MinRetainedVersions,
      filesystem = filesystem
    )

  trait Serdes[S] {
    def serialize(state: S): Array[Byte]
    def deserialize(bytes: Array[Byte]): Try[S]
  }
}
