package stasis.core.persistence.backends.file.container.ops

import java.nio.file.{Files, Path}
import java.nio.{ByteBuffer, ByteOrder}

import org.apache.pekko.Done

import scala.concurrent.{ExecutionContext, Future}

/**
  * Common file-based operations (internal API).
  */
private[ops] object FileOps extends AutoCloseSupport {

  /**
    * Creates a new file with the specified path, including any parent directories.
    *
    * @param path file to create
    * @return `Done`
    */
  def createFile(path: Path)(implicit ec: ExecutionContext): Future[Done] =
    createParentDirectories(path).map { _ =>
      val _ = Files.createFile(path)
      Done
    }

  /**
    * Creates all parent directories of the specified path.
    *
    * @param path path to use for creating parent directories
    * @return `Done`
    */
  def createParentDirectories(path: Path)(implicit ec: ExecutionContext): Future[Done] =
    Future {
      val _ = Files.createDirectories(path.getParent)
      Done
    }

  /**
    * Deletes the file at the specified path.
    *
    * @param path file to delete
    * @return `Done`
    */
  def destroyFile(path: Path)(implicit ec: ExecutionContext): Future[Done] =
    Future {
      Files.delete(path)
      Done
    }

  /**
    * Checks if the file at the specified path exists.
    *
    * @param path file to check
    * @return `true`, if the file exists
    */
  def fileExists(path: Path)(implicit ec: ExecutionContext): Future[Boolean] =
    Future {
      Files.exists(path)
    }

  /**
    * Writes the supplied header data at the beginning of the file with the specified path.
    *
    * @param path file to write
    * @param entrySize size of each entry in the file (padding will be added after the header up to this size)
    * @param header header data to write
    * @return `Done`
    */
  def writeHeader(path: Path, entrySize: Int, header: Array[Byte])(implicit ec: ExecutionContext): Future[Done] =
    Future {
      using(Files.newOutputStream(path)) { stream =>
        val paddingSize = entrySize - header.length

        val padding = Array.fill[Byte](paddingSize)(0)

        stream.write(header)
        stream.write(padding)
        stream.flush()

        Done
      }
    }

  /**
    * Reads the header of the file with the specified path.
    *
    * @param path file to read
    * @param headerSize expected size of header
    * @param fromBytes function for constructing the header from the read bytes
    * @tparam H header type
    * @return the requested header
    */
  def readHeader[H](
    path: Path,
    headerSize: Int,
    fromBytes: Array[Byte] => Either[Throwable, H]
  )(implicit ec: ExecutionContext, byteOrder: ByteOrder): Future[H] =
    Future {
      val buffer = ByteBuffer.allocate(headerSize).order(byteOrder)

      using(Files.newInputStream(path)) { stream =>
        val _ = stream.read(buffer.array())
        fromBytes(buffer.array())
      }
    }.flatMap {
      case Left(e)       => Future.failed(e)
      case Right(header) => Future.successful(header)
    }
}
