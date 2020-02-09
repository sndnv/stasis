package stasis.client.compression

import akka.NotUsed
import akka.stream.scaladsl.{Compression => AkkaCompression, Flow}
import akka.util.ByteString

object Gzip extends Encoder with Decoder {
  override def compress: Flow[ByteString, ByteString, NotUsed] = AkkaCompression.gzip
  override def decompress: Flow[ByteString, ByteString, NotUsed] = AkkaCompression.gunzip()
}
