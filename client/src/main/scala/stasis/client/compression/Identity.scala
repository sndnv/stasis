package stasis.client.compression

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.util.ByteString

object Identity extends Encoder with Decoder {
  override def compress: Flow[ByteString, ByteString, NotUsed] = Flow[ByteString]
  override def decompress: Flow[ByteString, ByteString, NotUsed] = Flow[ByteString]
}
