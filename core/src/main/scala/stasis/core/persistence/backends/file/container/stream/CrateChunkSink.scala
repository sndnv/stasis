package stasis.core.persistence.backends.file.container.stream

import java.nio.channels.FileChannel
import java.nio.file.{Path, StandardOpenOption}
import java.nio.{ByteBuffer, ByteOrder}
import java.util.UUID

import akka.Done
import akka.stream._
import akka.stream.scaladsl.Sink
import akka.stream.stage.{GraphStageLogic, GraphStageWithMaterializedValue, InHandler}
import stasis.core.persistence.backends.file.container.CrateChunk
import stasis.core.persistence.backends.file.container.exceptions.ContainerSinkFailure
import stasis.core.persistence.backends.file.container.headers.ChunkHeader

import scala.concurrent.{Future, Promise}
import scala.util.control.NonFatal

@SuppressWarnings(
  Array(
    "org.wartremover.warts.NonUnitStatements",
    "org.wartremover.warts.Var",
    "org.wartremover.warts.Null",
    "org.wartremover.warts.Throw"
  )
)
class CrateChunkSink(
  path: Path,
  crate: UUID,
  maxChunkSize: Int,
  byteOrder: ByteOrder
) extends GraphStageWithMaterializedValue[SinkShape[CrateChunk], Future[Done]] {
  val in: Inlet[CrateChunk] = Inlet("CrateChunkSink")

  override val shape: SinkShape[CrateChunk] = SinkShape(in)

  override def toString: String =
    s"CrateChunkSink(path=${path.toString}, crate=${crate.toString}, maxChunkSize=${maxChunkSize.toString})"

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[Done]) = {
    val resultPromise = Promise[Done]()

    val logic: GraphStageLogic = new GraphStageLogic(shape) with InHandler { handler =>
      val headerBuffer: ByteBuffer = ByteBuffer.allocate(ChunkHeader.HEADER_SIZE).order(byteOrder)
      val chunkBuffer: ByteBuffer = ByteBuffer.allocate(maxChunkSize).order(byteOrder)
      var channel: FileChannel = _

      setHandler(in, handler)

      override def preStart(): Unit = {
        try {
          channel = FileChannel.open(path, StandardOpenOption.APPEND)
        } catch {
          case NonFatal(e) =>
            completeWithFailure(
              ContainerSinkFailure(
                s"Failed to open channel for file [${path.toString}]: " +
                  s"[${e.getClass.getSimpleName}: ${e.getMessage}]"
              )
            )
        }

        pull(in)
      }

      override def postStop(): Unit = {
        resultPromise.trySuccess(Done)
        Option(channel).foreach(_.close())
      }

      override def onPush(): Unit = {
        val CrateChunk(header, data) = grab(in)

        try {
          require(data.nonEmpty, "Cannot store empty chunks")

          headerBuffer.put(ChunkHeader.toBytes(header)(byteOrder)).flip()
          chunkBuffer.put(data.padTo(maxChunkSize, 0: Byte).toArray).flip()

          val expectedBytesWritten = ChunkHeader.HEADER_SIZE + maxChunkSize
          val actualBytesWritten = channel.write(Array(headerBuffer, chunkBuffer))

          require(
            actualBytesWritten == expectedBytesWritten,
            s"Expected to write [${expectedBytesWritten.toString}] byte(s) but only [${actualBytesWritten.toString}] were written"
          )
        } catch {
          case NonFatal(e) =>
            completeWithFailure(
              ContainerSinkFailure(
                s"Failed to write chunk [${header.chunkId.toString}] for crate [${crate.toString}] to file [${path.toString}]: " +
                  s"[${e.getClass.getSimpleName}: ${e.getMessage}]"
              )
            )
        }

        headerBuffer.clear()
        chunkBuffer.clear()

        pull(in)
      }

      private def completeWithFailure(e: Throwable): Nothing = {
        failStage(e)
        resultPromise.tryFailure(e)
        throw e
      }
    }

    (logic, resultPromise.future)
  }
}

object CrateChunkSink {
  def apply(
    path: Path,
    crate: UUID,
    maxChunkSize: Int
  )(implicit byteOrder: ByteOrder): Sink[CrateChunk, Future[Done]] =
    Sink
      .fromGraph(new CrateChunkSink(path, crate, maxChunkSize, byteOrder))
      .withAttributes(
        Attributes
          .name(name = "crateChunkSink")
          .and(ActorAttributes.IODispatcher)
      )
}
