package stasis.client.encryption.stream

import java.security.Key
import java.security.spec.AlgorithmParameterSpec

import javax.crypto.Cipher

import org.apache.pekko.stream.stage.GraphStage
import org.apache.pekko.stream.stage.GraphStageLogic
import org.apache.pekko.stream.stage.InHandler
import org.apache.pekko.stream.stage.OutHandler
import org.apache.pekko.stream.Attributes
import org.apache.pekko.stream.FlowShape
import org.apache.pekko.stream.Inlet
import org.apache.pekko.stream.Outlet
import org.apache.pekko.util.ByteString

class CipherStage(
  algorithm: String,
  cipherMode: String,
  padding: String,
  operationMode: Int,
  key: Key,
  spec: Option[AlgorithmParameterSpec]
) extends GraphStage[FlowShape[ByteString, ByteString]] {
  private val in: Inlet[ByteString] = Inlet[ByteString]("CipherStage.in")
  private val out: Outlet[ByteString] = Outlet[ByteString]("CipherStage.out")

  override def shape: FlowShape[ByteString, ByteString] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      val cipher: Cipher = Cipher.getInstance(s"$algorithm/$cipherMode/$padding")
      spec match {
        case Some(params) => cipher.init(operationMode, key, params)
        case None         => cipher.init(operationMode, key)
      }

      setHandler(
        in,
        new InHandler {
          override def onUpstreamFinish(): Unit = {
            val lastChunk = ByteString.fromArrayUnsafe(cipher.doFinal())

            if (lastChunk.nonEmpty) {
              emit(out, lastChunk)
            }

            completeStage()
          }

          override def onPush(): Unit =
            Option(cipher.update(grab(in).toArrayUnsafe())).map(ByteString.fromArrayUnsafe) match {
              case Some(nextChunk) => push(out, nextChunk)
              case None            => pull(in)
            }
        }
      )

      setHandler(
        out,
        new OutHandler {
          override def onPull(): Unit = pull(in)
        }
      )
    }
}
