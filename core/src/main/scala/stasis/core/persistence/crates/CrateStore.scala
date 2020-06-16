package stasis.core.persistence.crates

import akka.actor.typed.scaladsl.LoggerOps
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.{ByteString, Timeout}
import akka.{Done, NotUsed}
import com.typesafe.config.Config
import org.slf4j.LoggerFactory
import stasis.core.packaging.{Crate, Manifest}
import stasis.core.persistence.CrateStorageRequest
import stasis.core.persistence.backends.StreamingBackend
import stasis.core.persistence.backends.file.{ContainerBackend, FileBackend}
import stasis.core.persistence.backends.memory.StreamingMemoryBackend

import scala.concurrent.{ExecutionContext, Future}

class CrateStore(
  val backend: StreamingBackend
)(implicit system: ActorSystem[SpawnProtocol.Command]) { store =>
  private implicit val ec: ExecutionContext = system.executionContext

  private val log = LoggerFactory.getLogger(this.getClass.getName)

  def persist(manifest: Manifest, content: Source[ByteString, NotUsed]): Future[Done] = {
    log.debugN("Persisting content for crate [{}] with manifest [{}]", manifest.crate, manifest)

    for {
      sink <- sink(manifest.crate)
      result <- content.runWith(sink)
    } yield {
      log.debug("Persist completed for crate [{}]", manifest.crate)
      result
    }
  }

  def sink(crate: Crate.Id): Future[Sink[ByteString, Future[Done]]] = {
    log.debug("Retrieving content sink for crate [{}]", crate)

    backend
      .sink(crate)
      .map { sink =>
        log.debug("Content sink for crate [{}] removed", crate)
        sink
      }
  }

  def retrieve(crate: Crate.Id): Future[Option[Source[ByteString, NotUsed]]] = {
    log.debug("Retrieving content source for crate [{}]", crate)
    backend.source(crate)
  }

  def discard(crate: Crate.Id): Future[Boolean] = {
    log.debug("Discarding crate [{}]", crate)
    backend
      .delete(crate)
      .map { result =>
        if (result) {
          log.debug("Discarded crate [{}]", crate)
        } else {
          log.warn("Failed to discard crate [{}]; crate not found", crate)
        }

        result
      }
  }

  def canStore(request: CrateStorageRequest): Future[Boolean] =
    backend.canStore(request.size * request.copies)

  def view: CrateStoreView =
    new CrateStoreView {
      override def retrieve(crate: Crate.Id): Future[Option[Source[ByteString, NotUsed]]] =
        store.retrieve(crate)
    }
}

object CrateStore {
  sealed trait Descriptor {
    override def toString: String = Descriptor.asString(this)
  }

  object Descriptor {
    final case class ForStreamingMemoryBackend(maxSize: Long, maxChunkSize: Int, name: String) extends Descriptor
    final case class ForContainerBackend(path: String, maxChunkSize: Int, maxChunks: Int) extends Descriptor
    final case class ForFileBackend(parentDirectory: String) extends Descriptor

    def apply(config: Config): Descriptor =
      config.getString("type").toLowerCase match {
        case "memory" =>
          ForStreamingMemoryBackend(
            maxSize = config.getBytes("memory.max-size"),
            maxChunkSize = config.getBytes("memory.max-chunk-size").toInt,
            name = config.getString("memory.name")
          )

        case "container" =>
          ForContainerBackend(
            path = config.getString("container.path"),
            maxChunkSize = Math.toIntExact(config.getBytes("container.max-chunk-size")),
            maxChunks = config.getInt("container.max-chunks")
          )

        case "file" =>
          ForFileBackend(
            parentDirectory = config.getString("file.parent-directory")
          )
      }

    def asString(descriptor: Descriptor): String =
      descriptor match {
        case Descriptor.ForStreamingMemoryBackend(maxSize, maxChunkSize, name) =>
          s"StreamingMemoryBackend(maxSize=${maxSize.toString}, maxChunkSize=${maxChunkSize.toString}, name=$name)"

        case Descriptor.ForContainerBackend(path, maxChunkSize, maxChunks) =>
          s"ContainerBackend(path=$path, maxChunkSize=${maxChunkSize.toString}, maxChunks=${maxChunks.toString})"

        case Descriptor.ForFileBackend(parentDirectory) =>
          s"FileBackend(parentDirectory=$parentDirectory)"
      }
  }

  def fromDescriptor(
    descriptor: Descriptor
  )(implicit system: ActorSystem[SpawnProtocol.Command], timeout: Timeout): CrateStore = {
    implicit val ec: ExecutionContext = system.executionContext

    val backend = descriptor match {
      case Descriptor.ForStreamingMemoryBackend(maxSize, maxChunkSize, name) =>
        StreamingMemoryBackend(
          maxSize = maxSize,
          maxChunkSize = maxChunkSize,
          name = name
        )

      case Descriptor.ForContainerBackend(path, maxChunkSize, maxChunks) =>
        new ContainerBackend(
          path = path,
          maxChunkSize = maxChunkSize,
          maxChunks = maxChunks
        )

      case Descriptor.ForFileBackend(parentDirectory) =>
        new FileBackend(
          parentDirectory = parentDirectory
        )
    }

    new CrateStore(backend = backend)
  }
}
