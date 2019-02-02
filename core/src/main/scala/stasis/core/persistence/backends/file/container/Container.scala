package stasis.core.persistence.backends.file.container

import java.nio.ByteOrder
import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import akka.{Done, NotUsed}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.util.ByteString
import stasis.core.persistence.backends.file.container.exceptions.{ContainerFailure, ConversionFailure}
import stasis.core.persistence.backends.file.container.headers.ContainerHeader
import stasis.core.persistence.backends.file.container.ops.{ContainerLogOps, ContainerOps, ConversionOps}
import stasis.core.persistence.backends.file.container.stream.transform.{ChunksToCrate, CrateToChunks}
import stasis.core.persistence.backends.file.container.stream.{CrateChunkSink, CrateChunkSource}
import scala.concurrent.{ExecutionContext, Future}

class Container(
  path: String,
  val maxChunkSize: Int,
  val maxChunks: Int
)(implicit ec: ExecutionContext, byteOrder: ByteOrder) {
  val containerPath: Path = Paths.get(path)
  val containerLogPath: Path = Paths.get(s"${path}_log")

  def create(): Future[Done] =
    for {
      container <- ContainerOps.create(containerPath, maxChunkSize, maxChunks)
      _ <- ContainerLogOps.create(containerLogPath, container)
    } yield {
      Done
    }

  def destroy(): Future[Done] =
    exists.flatMap { containerExists =>
      if (containerExists) {
        for {
          _ <- ContainerLogOps.destroy(containerLogPath)
          _ <- ContainerOps.destroy(containerPath)
        } yield {
          Done
        }
      } else {
        Future.successful(Done)
      }
    }

  def exists: Future[Boolean] =
    for {
      logExists <- ContainerLogOps.exists(containerLogPath)
      containerExists <- ContainerOps.exists(containerPath)
    } yield {
      logExists && containerExists
    }

  def put(crate: UUID, crateData: ByteString): Future[Done] =
    for {
      _ <- canStore(crateData.length).flatMap { canStore =>
        if (canStore) {
          Future.successful(Done)
        } else {
          Future.failed(
            new ContainerFailure(
              s"Cannot put crate [$crate] in container [$containerPath]; not enough storage available"
            )
          )
        }
      }
      _ <- ContainerOps.put(containerPath, crate, crateData)
      _ <- ContainerLogOps.add(containerLogPath, crate)
    } yield {
      Done
    }

  def get(crate: UUID): Future[Option[ByteString]] =
    contains(crate).flatMap { exists =>
      if (exists) {
        ContainerOps.get(containerPath, crate)
      } else {
        Future.successful(None)
      }
    }

  def delete(crate: UUID): Future[Boolean] =
    contains(crate).flatMap { exists =>
      if (exists) {
        ContainerLogOps.remove(containerLogPath, crate).map(_ => true)
      } else {
        Future.successful(false)
      }
    }

  def contains(crate: UUID): Future[Boolean] =
    exists.flatMap { containerExists =>
      if (containerExists) {
        ContainerLogOps.crates(containerLogPath).map(_.contains(crate))
      } else {
        Future.successful(false)
      }
    }

  def canStore(bytes: Long): Future[Boolean] =
    ContainerOps.occupiedChunks(containerPath, maxChunkSize).map { occupiedChunks =>
      val requestedChunks = math.ceil(bytes.toDouble / maxChunkSize).toInt
      val availableChunks = maxChunks - occupiedChunks

      requestedChunks <= availableChunks
    }

  def sink(crate: UUID): Future[Sink[ByteString, Future[Done]]] =
    ContainerOps.occupiedChunks(containerPath, maxChunkSize).flatMap { occupiedChunks =>
      val availableChunks = maxChunks - occupiedChunks

      if (availableChunks > 0) {
        Future.successful(
          Flow
            .fromGraph(CrateToChunks(crate, maxChunkSize, chunkIdStart = 0))
            .toMat(CrateChunkSink(containerPath, crate, maxChunkSize))(Keep.right[NotUsed, Future[Done]])
            .mapMaterializedValue(_.flatMap(_ => ContainerLogOps.add(containerLogPath, crate)))
        )
      } else {
        Future.failed(
          new ContainerFailure(
            s"Cannot create sink for crate [$crate] in container [$containerPath]; not enough storage available"
          )
        )
      }
    }

  def source(crate: UUID): Future[Option[Source[ByteString, Future[Done]]]] =
    contains(crate).flatMap { exists =>
      if (exists) {
        ContainerOps.index(containerPath).map { index =>
          index.crates.get(crate).map { chunks =>
            CrateChunkSource(containerPath, maxChunkSize, chunks)
              .via(ChunksToCrate())
          }
        }
      } else {
        Future.successful(None)
      }
    }

  def compact()(implicit mat: ActorMaterializer): Future[Done] =
    for {
      crates <- ContainerLogOps.crates(containerLogPath)
      temporaryContainer = containerPath.resolveSibling(s"${containerPath.getFileName}.compact")
      _ <- ContainerOps.create(
        temporaryContainer,
        maxChunkSize = maxChunkSize,
        maxChunks = maxChunks
      )
      _ <- ContainerOps.filter(containerPath, temporaryContainer, header => crates.contains(header.crateId))
      _ <- destroy()
      _ <- Future {
        Files.move(temporaryContainer, containerPath)
      }
      _ <- rebuildLog()
    } yield {
      Done
    }

  def rebuildLog(): Future[Done] =
    for {
      index <- ContainerOps.index(containerPath)
      _ <- ContainerLogOps.exists(containerLogPath).flatMap { logExists =>
        if (logExists) {
          ContainerLogOps.destroy(containerLogPath)
        } else {
          Future.successful(Done)
        }
      }
      _ <- ContainerLogOps.create(containerLogPath, index.container)
      _ <- Future.sequence(index.crates.keys.map(crate => ContainerLogOps.add(containerLogPath, crate)))
    } yield {
      Done
    }
}

object Container {
  final case class Index(
    container: ContainerHeader,
    crates: Map[UUID, Seq[CrateChunkDescriptor]],
    failed: Map[Index.ChunkEntryNumber, Index.IndexingFailure]
  )

  object Index {
    type ChunkEntryNumber = Long
    type IndexingFailure = String
  }

  final case class LogEntry(crate: UUID, event: LogEntry.Event)

  object LogEntry {
    final val ENTRY_SIZE: Int =
      Seq(
        java.lang.Long.BYTES, // crate / most significant bits
        java.lang.Long.BYTES, // crate / least significant bits
        java.lang.Byte.BYTES, // event
        ConversionOps.CRC_SIZE // crc
      ).sum

    def toBytes(entry: LogEntry)(implicit byteOrder: ByteOrder): Array[Byte] =
      ConversionOps.toBytes(
        putObjectFields = { buffer =>
          val _ = buffer
            .putLong(entry.crate.getMostSignificantBits)
            .putLong(entry.crate.getLeastSignificantBits)
            .put(Event.toByte(entry.event))
        },
        expectedObjectSize = ENTRY_SIZE
      )

    def fromBytes(bytes: Array[Byte])(implicit byteOrder: ByteOrder): Either[Throwable, LogEntry] =
      ConversionOps.fromBytes(
        getObjectFields = { buffer =>
          LogEntry(
            crate = new UUID(buffer.getLong, buffer.getLong),
            event = Event.fromByte(buffer.get())
          )
        },
        bytes = bytes,
        expectedObjectSize = ENTRY_SIZE
      )

    sealed trait Event
    final case object Add extends Event
    final case object Remove extends Event

    object Event {
      def toByte(event: Event): Byte =
        event match {
          case Add    => 1: Byte
          case Remove => 2: Byte
        }

      @SuppressWarnings(Array("org.wartremover.warts.Throw"))
      def fromByte(byte: Byte): Event =
        byte match {
          case 1 => Add
          case 2 => Remove
          case _ => throw ConversionFailure(s"Failed to convert byte to event; unexpected value provided: [$byte]")
        }
    }
  }

}
