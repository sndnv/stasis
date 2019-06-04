package stasis.client.encryption.stream

import java.security.Key
import java.security.spec.AlgorithmParameterSpec

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.util.ByteString
import javax.crypto.Cipher
import javax.crypto.spec.{GCMParameterSpec, SecretKeySpec}

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

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    val cipher: Cipher = Cipher.getInstance(s"$algorithm/$cipherMode/$padding")
    spec match {
      case Some(params) => cipher.init(operationMode, key, params)
      case None         => cipher.init(operationMode, key)
    }

    setHandler(
      in,
      new InHandler {
        override def onUpstreamFinish(): Unit = {
          val lastChunk = ByteString.fromArray(cipher.doFinal())

          if (lastChunk.nonEmpty) {
            emit(out, lastChunk)
          }

          completeStage()
        }

        override def onPush(): Unit =
          Option(cipher.update(grab(in).toArray)).map(ByteString.fromArray) match {
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

object CipherStage {
  def aesEncryption(key: ByteString, iv: ByteString): CipherStage =
    new CipherStage(
      algorithm = "AES",
      cipherMode = "GCM",
      padding = "NoPadding",
      operationMode = Cipher.ENCRYPT_MODE,
      key = new SecretKeySpec(key.toArray, "AES"),
      spec = Some(new GCMParameterSpec(128, iv.toArray))
    )

  def aesDecryption(key: ByteString, iv: ByteString): CipherStage =
    new CipherStage(
      algorithm = "AES",
      cipherMode = "GCM",
      padding = "NoPadding",
      operationMode = Cipher.DECRYPT_MODE,
      key = new SecretKeySpec(key.toArray, "AES"),
      spec = Some(new GCMParameterSpec(128, iv.toArray))
    )
}
