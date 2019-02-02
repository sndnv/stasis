package stasis.persistence.backends.file.container.stream.transform

import java.nio.{ByteBuffer, ByteOrder}
import java.util.UUID

import scala.collection.mutable

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.util.ByteString
import stasis.persistence.backends.file.container.CrateChunk
import stasis.persistence.backends.file.container.headers.ChunkHeader

@SuppressWarnings(
  Array(
    "org.wartremover.warts.NonUnitStatements",
    "org.wartremover.warts.Var"
  )
)
class CrateToChunks(
  crate: UUID,
  maxChunkSize: Int,
  chunkIdStart: Int,
  byteOrder: ByteOrder
) extends GraphStage[FlowShape[ByteString, CrateChunk]] {
  val in: Inlet[ByteString] = Inlet[ByteString]("CrateToChunks.in")
  val out: Outlet[CrateChunk] = Outlet[CrateChunk]("CrateToChunks.out")

  override def shape: FlowShape[ByteString, CrateChunk] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    val pending: mutable.Queue[CrateChunk] = mutable.Queue.empty
    val partial: ByteBuffer = ByteBuffer.allocate(maxChunkSize).order(byteOrder)
    var lastChunkId: Int = chunkIdStart

    setHandler(
      in,
      new InHandler {
        override def onUpstreamFinish(): Unit = {
          if (partial.position() != 0) {
            partial.flip()
            val data = ByteString.fromByteBuffer(partial)

            pending.enqueue(
              CrateChunk(
                header = ChunkHeader(
                  crateId = crate,
                  chunkId = lastChunkId,
                  chunkSize = data.length
                ),
                data
              )
            )
          }

          if (pending.nonEmpty) {
            emitMultiple(out, pending.toIterator)
          }

          completeStage()
        }

        override def onPush(): Unit = {
          val (lastPartialRemaining, remaining) = grab(in).splitAt(partial.remaining())
          val chunks = remaining.grouped(maxChunkSize)

          partial.put(lastPartialRemaining.toArray)

          if (!partial.hasRemaining) {
            partial.flip()
            val data = ByteString.fromByteBuffer(partial)

            pending.enqueue(
              CrateChunk(
                header = ChunkHeader(
                  crateId = crate,
                  chunkId = lastChunkId,
                  chunkSize = data.length
                ),
                data
              )
            )

            lastChunkId += 1

            partial.clear()
          }

          chunks.foreach[Unit] { chunk =>
            if (chunk.lengthCompare(maxChunkSize) == 0) {
              pending.enqueue(
                CrateChunk(
                  header = ChunkHeader(
                    crateId = crate,
                    chunkId = lastChunkId,
                    chunkSize = chunk.length
                  ),
                  data = chunk
                )
              )

              lastChunkId += 1
            } else {
              partial.put(chunk.toArray)
            }
          }

          if (pending.nonEmpty) {
            push(out, pending.dequeue())
          } else {
            pull(in)
          }
        }
      }
    )

    setHandler(
      out,
      new OutHandler {
        override def onPull(): Unit =
          if (pending.nonEmpty) {
            push(out, pending.dequeue())
          } else {
            pull(in)
          }
      }
    )
  }
}

object CrateToChunks {
  def apply(
    crate: UUID,
    maxChunkSize: Int,
    chunkIdStart: Int
  )(implicit byteOrder: ByteOrder): GraphStage[FlowShape[ByteString, CrateChunk]] =
    new CrateToChunks(crate, maxChunkSize, chunkIdStart, byteOrder)
}
