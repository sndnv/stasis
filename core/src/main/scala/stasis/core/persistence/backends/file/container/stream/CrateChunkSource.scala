package stasis.core.persistence.backends.file.container.stream

import java.nio.channels.FileChannel
import java.nio.file.{Path, StandardOpenOption}
import java.nio.{ByteBuffer, ByteOrder}

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.util.control.NonFatal

import akka.Done
import akka.stream._
import akka.stream.scaladsl.Source
import akka.stream.stage.{GraphStageLogic, GraphStageWithMaterializedValue, OutHandler}
import akka.util.ByteString
import stasis.core.persistence.backends.file.container.exceptions.ContainerSourceFailure
import stasis.core.persistence.backends.file.container.{CrateChunk, CrateChunkDescriptor}

@SuppressWarnings(
  Array(
    "org.wartremover.warts.NonUnitStatements",
    "org.wartremover.warts.Var",
    "org.wartremover.warts.Null",
    "org.wartremover.warts.Throw"
  )
)
class CrateChunkSource(
  path: Path,
  maxChunkSize: Int,
  chunks: Seq[CrateChunkDescriptor],
  byteOrder: ByteOrder
) extends GraphStageWithMaterializedValue[SourceShape[CrateChunk], Future[Done]] {
  val out: Outlet[CrateChunk] = Outlet[CrateChunk]("CrateChunkSource")

  override val shape: SourceShape[CrateChunk] = SourceShape(out)

  override def toString: String =
    s"CrateChunkSource(path=$path, maxChunkSize=$maxChunkSize, chunks=$chunks)"

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[Done]) = {
    val resultPromise = Promise[Done]()

    val logic: GraphStageLogic = new GraphStageLogic(shape) with OutHandler { handler =>
      val buffer: ByteBuffer = ByteBuffer.allocate(maxChunkSize).order(byteOrder)
      val remainingChunks: mutable.Queue[CrateChunkDescriptor] = chunks.to[mutable.Queue]
      var channel: FileChannel = _

      setHandler(out, handler)

      override def preStart(): Unit =
        try {
          channel = FileChannel.open(path, StandardOpenOption.READ)
        } catch {
          case NonFatal(e) =>
            completeWithFailure(
              ContainerSourceFailure(s"Failed to open channel for file [$path]: [$e]")
            )
        }

      override def postStop(): Unit = {
        resultPromise.trySuccess(Done)
        Option(channel).foreach(_.close())
      }

      override def onPull(): Unit =
        if (remainingChunks.nonEmpty) {
          val CrateChunkDescriptor(nextChunk, nextChunkDataOffset) = remainingChunks.dequeue()
          try {
            val bytesRead = channel.read(buffer, nextChunkDataOffset)

            require(
              bytesRead > 0,
              s"Expected chunk with size [${nextChunk.chunkSize}] but no bytes were read"
            )

            require(
              bytesRead >= nextChunk.chunkSize,
              s"Expected chunk with size [${nextChunk.chunkSize}] but only [$bytesRead] byte(s) were read"
            )

            push(
              out,
              CrateChunk(
                header = nextChunk,
                data = ByteString.fromArray(
                  buffer.array(),
                  offset = 0,
                  length = nextChunk.chunkSize
                )
              )
            )

            buffer.clear()
          } catch {
            case NonFatal(e) =>
              completeWithFailure(
                ContainerSourceFailure(
                  s"Failed to read chunk [${nextChunk.chunkId}] for crate [${nextChunk.crateId}] " +
                    s"from file [$path] at offset [$nextChunkDataOffset]: [$e]"
                )
              )
          }
        } else {
          completeStage()
          resultPromise.trySuccess(Done)
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

object CrateChunkSource {
  def apply(
    path: Path,
    maxChunkSize: Int,
    chunks: Seq[CrateChunkDescriptor]
  )(implicit byteOrder: ByteOrder): Source[CrateChunk, Future[Done]] =
    Source
      .fromGraph(new CrateChunkSource(path, maxChunkSize, chunks, byteOrder))
      .withAttributes(
        Attributes
          .name("crateChunkSource")
          .and(ActorAttributes.IODispatcher)
      )
}
