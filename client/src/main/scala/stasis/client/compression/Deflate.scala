package stasis.client.compression

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Compression => AkkaCompression}
import akka.util.ByteString

object Deflate extends Encoder with Decoder {
  override def compress: Flow[ByteString, ByteString, NotUsed] = AkkaCompression.deflate
  override def decompress: Flow[ByteString, ByteString, NotUsed] = AkkaCompression.inflate()
}
