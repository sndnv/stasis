package stasis.core.persistence.backends.file.container.ops

import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.UUID

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.ByteString

import stasis.core.persistence.backends.file.container.Container
import stasis.core.persistence.backends.file.container.Container.Index.ChunkEntryNumber
import stasis.core.persistence.backends.file.container.Container.Index.IndexingFailure
import stasis.core.persistence.backends.file.container.CrateChunkDescriptor
import stasis.core.persistence.backends.file.container.exceptions.ContainerFailure
import stasis.core.persistence.backends.file.container.headers.ChunkHeader
import stasis.core.persistence.backends.file.container.headers.ContainerHeader
import stasis.core.persistence.backends.file.container.stream.CrateChunkSink
import stasis.core.persistence.backends.file.container.stream.CrateChunkSource

@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
object ContainerOps extends AutoCloseSupport {
  def create(
    path: Path,
    maxChunkSize: Int,
    maxChunks: Int
  )(implicit ec: ExecutionContext, byteOrder: ByteOrder): Future[ContainerHeader] = {
    val result = for {
      _ <- FileOps.createFile(path)
      header = ContainerHeader(maxChunkSize = maxChunkSize, maxChunks = maxChunks)
      _ <- FileOps.writeHeader(path, ChunkHeader.HEADER_SIZE + maxChunkSize, ContainerHeader.toBytes(header))
    } yield {
      header
    }

    result
      .recoverWith { case NonFatal(e) =>
        Future.failed(
          new ContainerFailure(
            s"Failed to create container [${path.toString}]: " +
              s"[${e.getClass.getSimpleName}: ${e.getMessage}]"
          )
        )
      }
  }

  def destroy(
    path: Path
  )(implicit ec: ExecutionContext): Future[Done] =
    FileOps
      .destroyFile(path)
      .recoverWith { case NonFatal(e) =>
        Future.failed(
          new ContainerFailure(
            s"Failed to destroy container [${path.toString}]: " +
              s"[${e.getClass.getSimpleName}: ${e.getMessage}]"
          )
        )
      }

  def exists(path: Path)(implicit ec: ExecutionContext, byteOrder: ByteOrder): Future[Boolean] =
    FileOps.fileExists(path).flatMap { exists =>
      if (exists) {
        FileOps.readHeader(path, ContainerHeader.HEADER_SIZE, ContainerHeader.fromBytes).map(_ => true)
      } else {
        Future.successful(false)
      }
    }

  def index(
    path: Path
  )(implicit ec: ExecutionContext, byteOrder: ByteOrder): Future[Container.Index] =
    FileOps
      .readHeader(path, ContainerHeader.HEADER_SIZE, ContainerHeader.fromBytes)
      .flatMap { containerHeader =>
        val chunkEntrySize = ChunkHeader.HEADER_SIZE + containerHeader.maxChunkSize
        val containerHeaderSize = chunkEntrySize
        val expectedChunks = (Files.size(path) - containerHeaderSize) / chunkEntrySize

        readChunkHeaders(path, chunkEntrySize, expectedChunks).map { case (chunks, failed) =>
          Container.Index(
            container = containerHeader,
            crates = chunks.groupBy(_.header.crateId),
            failed = failed
          )
        }
      }
      .recoverWith { case NonFatal(e) =>
        Future.failed(
          new ContainerFailure(
            s"Failed to load container [${path.toString}]: " +
              s"[${e.getClass.getSimpleName}: ${e.getMessage}]"
          )
        )
      }

  def put(
    path: Path,
    crate: UUID,
    crateData: ByteString
  )(implicit ec: ExecutionContext, byteOrder: ByteOrder): Future[Done] =
    FileOps
      .readHeader(path, ContainerHeader.HEADER_SIZE, ContainerHeader.fromBytes)
      .map { containerHeader =>
        val buffer = ByteBuffer.allocate(ChunkHeader.HEADER_SIZE + containerHeader.maxChunkSize).order(byteOrder)

        using(Files.newOutputStream(path, StandardOpenOption.APPEND)) { stream =>
          crateData
            .grouped(containerHeader.maxChunkSize)
            .zipWithIndex
            .foreach { case (chunk, index) =>
              val header = ChunkHeader(
                crateId = crate,
                chunkId = index,
                chunkSize = chunk.length
              )

              buffer
                .put(ChunkHeader.toBytes(header))
                .put(chunk.padTo(containerHeader.maxChunkSize, 0: Byte).toArray)

              stream.write(buffer.array())

              buffer.clear()
            }

          stream.flush()

          Done
        }
      }
      .recoverWith { case NonFatal(e) =>
        Future.failed(
          new ContainerFailure(
            s"Failed to read container header [${path.toString}]: " +
              s"[${e.getClass.getSimpleName}: ${e.getMessage}]"
          )
        )
      }

  def get(
    path: Path,
    crate: UUID
  )(implicit ec: ExecutionContext, byteOrder: ByteOrder): Future[Option[ByteString]] =
    index(path).map { index =>
      index.crates.get(crate).flatMap { chunks =>
        val buffer = ByteBuffer.allocate(index.container.maxChunkSize).order(byteOrder)

        using(new RandomAccessFile(path.toFile, "r")) { container =>
          val crateChunks = chunks.map { case CrateChunkDescriptor(chunkHeader, dataStartOffset) =>
            container.seek(dataStartOffset)
            container.readFully(buffer.array(), 0, chunkHeader.chunkSize)
            val chunk = ByteString.fromArray(buffer.array(), 0, chunkHeader.chunkSize)
            buffer.clear()
            chunk
          }

          crateChunks.reduceOption(_.concat(_))
        }
      }
    }

  def filter(
    source: Path,
    target: Path,
    p: ChunkHeader => Boolean
  )(implicit system: ActorSystem[Nothing], byteOrder: ByteOrder): Future[Done] = {
    implicit val ec: ExecutionContext = system.executionContext

    index(source)
      .flatMap { sourceIndex =>
        Future
          .sequence(
            sourceIndex.crates.map { case (crate, chunks) =>
              CrateChunkSource(source, sourceIndex.container.maxChunkSize, chunks)
                .filter(chunk => p(chunk.header))
                .runWith(CrateChunkSink(target, crate, sourceIndex.container.maxChunkSize))
            }
          )
      }
      .map(_ => Done)
  }

  def occupiedChunks(path: Path, maxChunkSize: Int)(implicit ec: ExecutionContext): Future[Int] =
    Future {
      val chunkEntrySize = ChunkHeader.HEADER_SIZE + maxChunkSize
      val containerHeaderSize = chunkEntrySize
      val occupiedChunks = (Files.size(path) - containerHeaderSize) / chunkEntrySize

      require(occupiedChunks >= 0, "Provided container is not valid")

      occupiedChunks.toInt
    }.recoverWith { case NonFatal(e) =>
      Future.failed(
        new ContainerFailure(
          s"Failed to calculate occupied chunks for container [${path.toString}]: " +
            s"[${e.getClass.getSimpleName}: ${e.getMessage}]"
        )
      )
    }

  private def readChunkHeaders(
    path: Path,
    chunkEntrySize: Int,
    expectedChunks: Long
  )(implicit
    ec: ExecutionContext,
    byteOrder: ByteOrder
  ): Future[(Seq[CrateChunkDescriptor], Map[ChunkEntryNumber, IndexingFailure])] =
    Future {
      val buffer = ByteBuffer.allocate(ChunkHeader.HEADER_SIZE).order(byteOrder)

      using(new RandomAccessFile(path.toFile, "r")) { container =>
        val chunkEntries =
          (1L to expectedChunks)
            .map { entry =>
              val entryStartOffset = entry * chunkEntrySize
              val dataStartOffset = entryStartOffset + ChunkHeader.HEADER_SIZE
              container.seek(entryStartOffset)
              container.readFully(buffer.array())

              val parsed = ChunkHeader.fromBytes(buffer.array())
              buffer.clear()

              (entry, dataStartOffset, parsed)
            }

        val chunks = chunkEntries.collect { case (_, dataStartOffset, Right(header)) =>
          CrateChunkDescriptor(header, dataStartOffset)
        }

        val failed = chunkEntries.collect { case (entry, _, Left(e)) =>
          (entry, e.getMessage)
        }.toMap

        (chunks, failed)
      }
    }
}
