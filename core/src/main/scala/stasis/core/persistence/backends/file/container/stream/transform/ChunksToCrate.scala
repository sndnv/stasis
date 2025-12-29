package stasis.core.persistence.backends.file.container.stream.transform

import org.apache.pekko.stream.stage.GraphStage
import org.apache.pekko.stream.stage.GraphStageLogic
import org.apache.pekko.stream.stage.InHandler
import org.apache.pekko.stream.stage.OutHandler
import org.apache.pekko.stream.Attributes
import org.apache.pekko.stream.FlowShape
import org.apache.pekko.stream.Inlet
import org.apache.pekko.stream.Outlet
import org.apache.pekko.util.ByteString

import stasis.core.persistence.backends.file.container.CrateChunk

class ChunksToCrate extends GraphStage[FlowShape[CrateChunk, ByteString]] {
  val in: Inlet[CrateChunk] = Inlet[CrateChunk]("ChunksToCrate.in")
  val out: Outlet[ByteString] = Outlet[ByteString]("ChunksToCrate.out")

  override val shape: FlowShape[CrateChunk, ByteString] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      setHandler(
        in,
        new InHandler {
          override def onPush(): Unit =
            push(out, grab(in).data)
        }
      )

      setHandler(
        out,
        new OutHandler {
          override def onPull(): Unit =
            pull(in)
        }
      )
    }

}

object ChunksToCrate {
  def apply(): GraphStage[FlowShape[CrateChunk, ByteString]] =
    new ChunksToCrate()
}
