package stasis.core.persistence.backends.file.container.ops

import java.io.RandomAccessFile
import java.nio.file.{Files, Path, StandardOpenOption}
import java.nio.{ByteBuffer, ByteOrder}
import java.util.UUID

import akka.Done
import stasis.core.persistence.backends.file.container.Container
import stasis.core.persistence.backends.file.container.exceptions.ContainerFailure
import stasis.core.persistence.backends.file.container.headers.{ContainerHeader, ContainerLogHeader}

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
object ContainerLogOps extends AutoCloseSupport {
  private val containerLogEntrySize: Int = math.max(ContainerLogHeader.HEADER_SIZE, Container.LogEntry.ENTRY_SIZE)

  def create(
    path: Path,
    container: ContainerHeader
  )(implicit ec: ExecutionContext, byteOrder: ByteOrder): Future[ContainerLogHeader] = {
    val result = for {
      _ <- FileOps.createFile(path)
      header = ContainerLogHeader(container)
      _ <- FileOps.writeHeader(path, containerLogEntrySize, ContainerLogHeader.toBytes(header))
    } yield {
      header
    }

    result.recoverWith { case NonFatal(e) =>
      Future.failed(
        new ContainerFailure(
          s"Failed to create container log [${path.toString}]: " +
            s"[${e.getClass.getSimpleName}: ${e.getMessage}]"
        )
      )
    }
  }

  def destroy(path: Path)(implicit ec: ExecutionContext): Future[Done] =
    FileOps
      .destroyFile(path)
      .recoverWith { case NonFatal(e) =>
        Future.failed(
          new ContainerFailure(
            s"Failed to destroy container log [${path.toString}]: " +
              s"[${e.getClass.getSimpleName}: ${e.getMessage}]"
          )
        )
      }

  def exists(path: Path)(implicit ec: ExecutionContext, byteOrder: ByteOrder): Future[Boolean] =
    FileOps.fileExists(path).flatMap { exists =>
      if (exists) {
        FileOps.readHeader(path, ContainerLogHeader.HEADER_SIZE, ContainerLogHeader.fromBytes).map(_ => true)
      } else {
        Future.successful(false)
      }
    }

  def add(path: Path, crate: UUID)(implicit ec: ExecutionContext, byteOrder: ByteOrder): Future[Done] =
    addLogEntry(path, crate, Container.LogEntry.Add)

  def remove(path: Path, crate: UUID)(implicit ec: ExecutionContext, byteOrder: ByteOrder): Future[Done] =
    addLogEntry(path, crate, Container.LogEntry.Remove)

  def crates(path: Path)(implicit ec: ExecutionContext, byteOrder: ByteOrder): Future[Set[UUID]] =
    readLogEntries(path)
      .map { entries =>
        entries.foldLeft(Set.empty[UUID]) { case (crates, entry) =>
          entry.event match {
            case Container.LogEntry.Add =>
              crates + entry.crate

            case Container.LogEntry.Remove =>
              crates - entry.crate
          }
        }
      }
      .recoverWith { case NonFatal(e) =>
        Future.failed(
          new ContainerFailure(
            s"Failed to retrieve crates from container log [${path.toString}]: " +
              s"[${e.getClass.getSimpleName}: ${e.getMessage}]"
          )
        )
      }

  private def addLogEntry(
    path: Path,
    crate: UUID,
    event: Container.LogEntry.Event
  )(implicit ec: ExecutionContext, byteOrder: ByteOrder): Future[Done] =
    Future {
      using(Files.newOutputStream(path, StandardOpenOption.APPEND)) { stream =>
        val buffer = ByteBuffer.allocate(containerLogEntrySize).order(byteOrder)
        val logEntry = Container.LogEntry.toBytes(Container.LogEntry(crate, event))

        buffer.put(logEntry.padTo(containerLogEntrySize, 0: Byte))
        stream.write(buffer.array())
        buffer.clear()
        stream.flush()

        Done
      }
    }.recoverWith { case NonFatal(e) =>
      Future.failed(
        new ContainerFailure(
          s"Failed to update container log [${path.toString}] with event [${event.toString}]: " +
            s"[${e.getClass.getSimpleName}: ${e.getMessage}]"
        )
      )
    }

  private def readLogEntries(
    path: Path
  )(implicit ec: ExecutionContext, byteOrder: ByteOrder): Future[Seq[Container.LogEntry]] =
    Future {
      val buffer = ByteBuffer.allocate(Container.LogEntry.ENTRY_SIZE).order(byteOrder)

      val expectedEntries = (Files.size(path) - containerLogEntrySize) / containerLogEntrySize

      using(new RandomAccessFile(path.toFile, "r")) { container =>
        @tailrec
        def readNextEntry(
          remainingEntries: Seq[Long],
          readEntries: Seq[Container.LogEntry]
        ): Either[Throwable, Seq[Container.LogEntry]] =
          remainingEntries.toList match {
            case nextEntry :: remaining =>
              container.seek(nextEntry * containerLogEntrySize)
              container.readFully(buffer.array())

              Container.LogEntry.fromBytes(buffer.array()) match {
                case Left(e) =>
                  Left(
                    new ContainerFailure(
                      s"Failed to read entry [${nextEntry.toString}] from container log [${path.toString}]: " +
                        s"[${e.getClass.getSimpleName}: ${e.getMessage}]"
                    )
                  )

                case Right(entry) =>
                  buffer.clear()
                  readNextEntry(remainingEntries = remaining, readEntries = readEntries :+ entry)
              }

            case _ =>
              Right(readEntries)
          }

        readNextEntry(remainingEntries = 1L to expectedEntries, readEntries = Seq.empty)
      }
    }.flatMap {
      case Left(e)       => Future.failed(e)
      case Right(header) => Future.successful(header)
    }
}
