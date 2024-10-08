package stasis.test.specs.unit.core.persistence.backends.file.container

import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.util.ByteString

import stasis.core.persistence.backends.file.container.headers.ChunkHeader
import stasis.core.persistence.backends.file.container.headers.ContainerLogHeader
import stasis.core.persistence.backends.file.container.Container
import stasis.core.persistence.backends.file.container.CrateChunk
import stasis.core.persistence.backends.file.container.CrateChunkDescriptor

object TestOps {
  def fileExists(path: Path): Boolean =
    Files.exists(path)

  def fileSize(path: Path): Long =
    Files.size(path)

  def createEmptyFile(path: Path): Unit = {
    Files.createDirectories(path.getParent)
    Files.createFile(path)
  }

  def writeChunks(
    path: Path,
    chunks: Seq[CrateChunk],
    maxChunkSize: Int
  )(implicit ec: ExecutionContext, byteOrder: ByteOrder): Future[Done] =
    Future {
      val buffer = ByteBuffer.allocate(ChunkHeader.HEADER_SIZE + maxChunkSize)
      val stream = Files.newOutputStream(path, StandardOpenOption.APPEND)

      chunks.foreach { case CrateChunk(header, chunk) =>
        buffer.put(ChunkHeader.toBytes(header))
        buffer.put(chunk.padTo(maxChunkSize, 0: Byte).toArray)
        stream.write(buffer.array())
        buffer.clear()
      }

      stream.flush()
      stream.close()

      Done
    }

  def readChunks(
    path: Path,
    maxChunkSize: Int,
    hasContainerHeader: Boolean
  )(implicit ec: ExecutionContext, byteOrder: ByteOrder): Future[Seq[CrateChunk]] =
    Future {
      val bytesToRead = ChunkHeader.HEADER_SIZE + maxChunkSize
      val buffer = ByteBuffer.allocate(bytesToRead).order(byteOrder)
      val stream = Files.newInputStream(path)

      if (hasContainerHeader) {
        val _ = stream.skip(bytesToRead.toLong)
      }

      @scala.annotation.tailrec
      def readNextBytes(partialChunks: Seq[Array[Byte]]): Seq[Array[Byte]] =
        if (stream.available() > 0) {
          val bytesRead = stream.read(buffer.array())
          val currentChunk = new Array[Byte](bytesRead)
          buffer.get(currentChunk)

          val updatedChunks = partialChunks :+ currentChunk
          buffer.clear()
          readNextBytes(partialChunks = updatedChunks)
        } else {
          partialChunks
        }

      val partialChunks = readNextBytes(partialChunks = Seq.empty).flatten
        .grouped(size = bytesToRead)
        .map { chunk =>
          val (headerBytes, chunkBytes) = chunk.splitAt(ChunkHeader.HEADER_SIZE)
          val header = ChunkHeader.fromBytes(headerBytes.toArray) match {
            case Left(e)      => throw e
            case Right(value) => value
          }

          CrateChunk(header, ByteString.fromArray(chunkBytes.take(header.chunkSize).toArray))
        }

      partialChunks.toSeq
    }

  def prepareContainerChunks(
    chunks: Seq[(UUID, ByteString)],
    maxChunkSize: Int,
    indexOffset: Int
  ): Seq[(UUID, CrateChunk, CrateChunkDescriptor)] =
    chunks.zipWithIndex.map { case ((crate, chunk), index) =>
      val startOffset = ChunkHeader.HEADER_SIZE + (index + indexOffset) * (ChunkHeader.HEADER_SIZE + maxChunkSize)

      val header = ChunkHeader(
        crateId = crate,
        chunkId = 0,
        chunkSize = chunk.length
      )

      (crate, CrateChunk(header, chunk), CrateChunkDescriptor(header, startOffset.toLong))
    }

  def readLogEntries(
    path: Path
  )(implicit ec: ExecutionContext, byteOrder: ByteOrder): Future[Seq[Container.LogEntry]] =
    Future {
      val entrySize = math.max(Container.LogEntry.ENTRY_SIZE, ContainerLogHeader.HEADER_SIZE)
      val bytesToRead = Container.LogEntry.ENTRY_SIZE
      val buffer = ByteBuffer.allocate(bytesToRead).order(byteOrder)
      val stream = Files.newInputStream(path)

      val _ = stream.skip(entrySize.toLong)

      @scala.annotation.tailrec
      def readNextBytes(entries: Seq[Array[Byte]]): Seq[Array[Byte]] =
        if (stream.available() > 0) {
          val bytesRead = stream.read(buffer.array())
          val currentEntry = new Array[Byte](bytesRead)
          buffer.get(currentEntry)

          val updatedEntries = entries :+ currentEntry
          buffer.clear()
          readNextBytes(entries = updatedEntries)
        } else {
          entries
        }

      val partialEntries = readNextBytes(entries = Seq.empty).flatten
        .grouped(size = entrySize)
        .map { entryBytes =>
          Container.LogEntry.fromBytes(entryBytes.take(bytesToRead).toArray) match {
            case Left(e)      => throw e
            case Right(value) => value
          }
        }

      partialEntries.toSeq
    }

  def corruptHeader(
    path: Path,
    entryNumber: Long,
    entrySize: Long,
    headerSize: Int
  )(implicit ec: ExecutionContext): Future[Done] =
    Future {
      val container = new RandomAccessFile(path.toFile, "rws")
      val entryHeaderStartOffset: Long = entryNumber * entrySize
      val updatedHeader = Array.fill[Byte](headerSize)(1: Byte)

      container.seek(entryHeaderStartOffset)
      container.write(updatedHeader)
      container.close()

      Done
    }
}
