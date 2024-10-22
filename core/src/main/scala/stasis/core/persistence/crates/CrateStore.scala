package stasis.core.persistence.crates

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

import com.typesafe.config.Config
import org.apache.pekko.Done
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.apache.pekko.util.Timeout
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.core.packaging.Crate
import stasis.core.packaging.Manifest
import stasis.core.persistence.CrateStorageRequest
import stasis.core.persistence.backends.StreamingBackend
import stasis.core.persistence.backends.file.ContainerBackend
import stasis.core.persistence.backends.file.FileBackend
import stasis.core.persistence.backends.memory.StreamingMemoryBackend
import stasis.layers.telemetry.TelemetryContext

class CrateStore(
  val backend: StreamingBackend
)(implicit system: ActorSystem[Nothing]) { store =>
  import CrateStore._

  private implicit val ec: ExecutionContext = system.executionContext

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  locally { val _ = init() }

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

  def view: CrateStore.View =
    new CrateStore.View {
      override def retrieve(crate: Crate.Id): Future[Option[Source[ByteString, NotUsed]]] =
        store.retrieve(crate)
    }

  protected def init(): Future[Done] = backend.loggedInit()
}

object CrateStore {
  trait View {
    def retrieve(crate: Crate.Id): Future[Option[Source[ByteString, NotUsed]]]
  }

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
  )(implicit system: ActorSystem[Nothing], telemetry: TelemetryContext, timeout: Timeout): CrateStore = {
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
        FileBackend(parentDirectory = parentDirectory)
    }

    new CrateStore(backend = backend)
  }

  implicit class ExtendedStreamingBackend(backend: StreamingBackend) {
    def loggedInit()(implicit log: Logger, ec: ExecutionContext): Future[Done] = {
      val result = backend.available().flatMap {
        case true =>
          log.debugN("Skipping initialization of backend [{}]; it already exists", backend.info)
          Future.successful(Done)

        case false =>
          log.debugN("Initializing backend [{}]", backend.info)
          backend.init()
      }

      result.onComplete {
        case Success(_) =>
          log.debugN(
            "Backend [{}] successfully initialized",
            backend.info
          )

        case Failure(e) =>
          log.errorN(
            "Failed to initialize backend [{}]: [{} - {}]",
            backend.info,
            e.getClass.getSimpleName,
            e.getMessage
          )
      }

      result
    }
  }
}
