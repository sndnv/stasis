package stasis.client.compression

import akka.NotUsed
import akka.stream.scaladsl.{Compression => AkkaCompression, Flow}
import akka.util.ByteString

object Deflate extends Encoder with Decoder {
  override val name: String = "deflate"
  override def compress: Flow[ByteString, ByteString, NotUsed] = AkkaCompression.deflate
  override def decompress: Flow[ByteString, ByteString, NotUsed] = AkkaCompression.inflate()
}
